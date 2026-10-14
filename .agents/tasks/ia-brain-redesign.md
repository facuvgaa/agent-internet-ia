# Diseño Técnico: Migración ia-master-brain a Arquitectura MCP + boto3

**Fecha:** 2025  
**Módulo:** `ia-master-brain/`  
**Decisión:** Opción C confirmada — LangGraph como orquestador, boto3/Bedrock directo, MCP Python SDK como cliente del back Java.

---

## Sección 1: Mapa del Estado Actual

### 1.1 Diagrama textual de la arquitectura actual

```
Kafka "consultas.usuario"
        │
        ▼
[consumer.py] ──── triage (llm_haiku vía langchain-aws) ──► route=CONSULTA → responder_consulta()
        │                                                                         │
        │ route=BRAIN                                                             ▼
        ▼                                                            Kafka "respuestas.agente"
[LlmBrain.run()]
        │
        ├─ intent=retention ──► RetentionGraph (langgraph + langchain tools)
        │
        ├─ intent=billing ───► BillingGraph (langgraph + langchain tools)
        │       │
        │       ├─ paso=ir_a_promise ──► PromiseGraph
        │       └─ paso=ir_a_retention ──► RetentionGraph
        │
        └─ intent=general ──► Grafo principal (LangGraph + ToolNode de LangChain)

Checkpointer: RedisSaver (langgraph-checkpoint-redis)
Fallback checkpointer: MemorySaver
DB conversación: Postgres (psycopg3)
```

### 1.2 Qué hace cada flow

| Flow | Archivo | Responsabilidad |
|------|---------|-----------------|
| **BillingGraph** | `flows/billings/` | Muestra facturas, gestiona reclamos (crea tickets), escala a promise/retention. Nodos: dispatcher → cargar_datos → conversar → [gestionar_reclamo \| info_servicios \| marcar_promise \| marcar_retention]. Usa 5 tools: `get_customer_info`, `billing_info`, `get_customer_service`, `create_ticket`, `payment_promises`. |
| **RetentionGraph** | `flows/retention/` | Verifica elegibilidad, genera ofertas de descuento por niveles 1-4, negocia con el cliente y aplica la retención. Nodos: dispatcher → cargar_datos → generar_oferta → negociar → aplicar. Usa 6 tools: `get_customer_info`, `get_customer_service`, `get_retention_eligibility`, `get_retention_tiers`, `get_retention_preview`, `apply_retention_agreement`. |
| **PromiseGraph** | `flows/promise/` | Registra promesas de pago sobre facturas vencidas. Nodos: cargar_datos → explicacion_promesa → ejecutar_promesa. Usa 3 tools: `get_customer_info`, `billing_info`, `payment_promises`. |
| **Grafo principal** | `agents/llm_brain.py` | Flujo general (consultas que no son billing/retention). LangChain ToolNode con todos los 16 tools. |
| **new_service** | `flows/new_service/` | Esqueleto vacío, sin implementar. |

### 1.3 Estado LangGraph actual

Hay tres `TypedDict` de estado, uno por flow:

**BillingEstate** (`flows/billings/state.py`):
```python
customer_id: int
cliente: Optional[dict]        # data de get_customer_info
facturas: list                 # data de billing_info
ticket_id: Optional[str]
servicios: Optional[list]      # data de get_customer_service
paso_actual: str               # dispatcher lógico
messages: Annotated[list[BaseMessage], add_messages]  # ← LangChain
```

**RetentionState** (`flows/retention/state.py`):
```python
customer_id: int
cliente: Optional[dict]
servicios: Optional[list]
eligibility: Optional[dict]
ofertas_preview: Optional[list]
paso_actual: str
messages: Annotated[list[BaseMessage], add_messages]  # ← LangChain
```

**PromiseEstate** (`flows/promise/state.py`):
```python
customer_id: int
cliente: Optional[dict]
factura_defeated: list
puede_prometer: bool
paso_actual: str
messages: Annotated[list[BaseMessage], add_messages]  # ← LangChain
```

**AgentState** (grafo principal, `agents/llm_brain.py`):
```python
messages: Annotated[list[BaseMessage], add_messages]
metadata: dict
dialog: dict
```

### 1.4 Checkpointing

- **RedisSaver** de `langgraph-checkpoint-redis` (Redis db=0).
- El consumer usa Redis db=1 por separado para ruteo de conversaciones (`route:$customer_id`).
- Fallback a `MemorySaver` si Redis no está disponible.
- Conversaciones también se persisten en Postgres `conversation-db` (tabla `conversation_history`), como backup de largo plazo.
- `get_checkpointer()` en `memory/memory_brain.py` maneja el singleton y el context manager de RedisSaver.

### 1.5 Flujo de un mensaje desde Kafka hasta la respuesta

```
1. consumer.py recibe msg de Kafka "consultas.usuario"
2. Extrae content y customer_id del JSON
3. Verifica route en Redis db=1:
   - Si "BRAIN" → procesar_brain()
   - Si "CONSULTA" → responder_consulta()
   - Si None → triage() para clasificar
4. triage(): llama a llm_haiku (LangChain ChatBedrockConverse) → "RECLAMO" | "CONSULTA"
5. RECLAMO → set_route("BRAIN"), procesar_brain()
6. procesar_brain() → LlmBrain.run():
   a. Verifica si hay conversación activa en subgrafos
   b. Detecta intent (billing/retention/general) con llm_base
   c. Deriva al subgrafo correspondiente
7. Subgrafo ejecuta nodos, llama tools (HTTP directo a cliente_back REST)
8. Retorna estado con messages
9. consumer.py extrae último AIMessage, limpia y envía a Kafka "respuestas.agente"
```

### 1.6 Tools actuales y cómo se llaman

Todos los tools están en `tools/tools.py` decorados con `@tool` de LangChain (`langchain.tools`). Hacen HTTP directo a:
- `BACK_API` → `/api/v1/internet-ia` (info cliente, facturas, tickets, diagnóstico, recargas)
- `BACK_RETENTION_API` → `/api/v1/retention` (elegibilidad, preview, apply)
- `BACK_SERVICE_API` → `/api/v1/available-services` (offerings)

Los 16 tools:
| Tool | Método | Endpoint |
|------|--------|----------|
| `get_customer_info` | GET | `/customers/{id}` |
| `get_customer_service` | GET | `/customers/services/{id}` |
| `create_ticket` | POST | `/tickets` |
| `billing_info` | GET | `/billing/customer/{id}` |
| `billing_lookup` | GET | `/billing/customer/{id}/lookup` |
| `payment_promises` | POST | `/payment-promises` |
| `grant_mobile_topup` | POST | `/mobile-topups` |
| `request_connection_reset` | POST | `/connection-resets` |
| `run_network_diagnostic` | POST | `/network-diagnostics` |
| `list_network_diagnostics` | GET | `/network-diagnostics/customers/{id}/services/{sid}` |
| `get_latest_network_diagnostic` | GET | `.../latest` |
| `get_retention_tiers` | GET | `/retention/tiers` |
| `get_retention_eligibility` | GET | `/retention/customers/{id}/eligibility` |
| `get_retention_preview` | POST | `/retention/preview` |
| `apply_retention_agreement` | POST | `/retention/applications` |
| `list_available_offerings` | GET | `/available-services/customers/{id}/offerings` |

### 1.7 Dependencias de LangChain vs independientes

**Dependencias de LangChain (a eliminar):**
- `langchain.tools.tool` → decorador `@tool` en `tools/tools.py`
- `langchain_core.messages.BaseMessage`, `HumanMessage`, `AIMessage`, `SystemMessage` → todos los flows y consumer
- `langchain_core.messages.add_messages` → reducer del estado en los TypedDict
- `langchain_aws.ChatBedrockConverse` → `connection_llm/llm_conecction.py`
- `langgraph.prebuilt.ToolNode` → `agents/llm_brain.py` (grafo principal)
- `langchain_core.messages.HumanMessage` → consumer.py (triage, responder_consulta)

**Independientes de LangChain (se mantienen):**
- `langgraph.graph.StateGraph`, `END` → todos los grafos
- `langgraph.checkpoint.redis.RedisSaver` → memory_brain.py  
  ⚠️ **IMPORTANTE:** `langgraph-checkpoint-redis 0.2.0+` tiene dependencia transitiva en `langchain-core >= 1.0.5`. Ver sección 4.
- `langgraph.checkpoint.memory.MemorySaver` → fallback
- `confluent_kafka` → consumer.py
- `boto3` → ya está en requirements, pero sin uso directo
- `psycopg` → save_to_db.py
- `redis` → consumer.py (ruteo db=1)
- `pydantic` → sin uso directo actual
- `python-dotenv` → todos los módulos

---

## Sección 2: Diseño de la Nueva Arquitectura

### 2.1 Nuevo grafo LangGraph (sin LangChain)

El diseño reemplaza los tres subgrafos separados por **un único grafo principal** con un nodo de routing que detecta el dominio, seguido de nodos agente por dominio que llaman al LLM via boto3 con los tools MCP. Esto simplifica el código y hace los workflows dinámicos.

**Grafo propuesto (`graph/agent_graph.py`):**

```python
from langgraph.graph import StateGraph, END
from graph.state import AgentState
from graph.nodes import (
    routing_node,
    billing_agent_node,
    retention_agent_node,
    promise_agent_node,
    general_agent_node,
)


def build_agent_graph(checkpointer):
    """
    Grafo principal unificado.

    Routing:
      START → routing_node → [billing | retention | promise | general] → END

    Cada nodo agente contiene su propio tool-use loop con boto3/Bedrock.
    El grafo NO usa subgrafos separados; el estado unificado AgentState
    es suficiente para todos los dominios.
    """
    workflow = StateGraph(AgentState)

    workflow.add_node("routing", routing_node)
    workflow.add_node("billing_agent", billing_agent_node)
    workflow.add_node("retention_agent", retention_agent_node)
    workflow.add_node("promise_agent", promise_agent_node)
    workflow.add_node("general_agent", general_agent_node)

    workflow.set_entry_point("routing")

    workflow.add_conditional_edges(
        "routing",
        lambda state: state["domain"],
        {
            "billing":   "billing_agent",
            "retention": "retention_agent",
            "promise":   "promise_agent",
            "general":   "general_agent",
        },
    )

    workflow.add_edge("billing_agent",   END)
    workflow.add_edge("retention_agent", END)
    workflow.add_edge("promise_agent",   END)
    workflow.add_edge("general_agent",   END)

    return workflow.compile(checkpointer=checkpointer)
```

**Lógica del routing_node:**

El `routing_node` llama al LLM Haiku (vía boto3) para clasificar el mensaje en `billing | retention | promise | general`. También puede leer el `domain` del estado previo (persistido en el checkpointer) para mantener la conversación activa, igual que la lógica de `LlmBrain.run()` actual.

```python
# graph/nodes.py

def routing_node(state: AgentState) -> dict:
    """
    Detecta dominio. Si hay domain activo y no está cerrado, lo mantiene.
    De lo contrario, llama al LLM Haiku para clasificar.
    """
    current_domain = state.get("domain")
    paso = state.get("paso_actual", "")
    DOMINIOS_CERRADOS = {"cerrado", "no_elegible", "sin_ofertas",
                         "retencion_aplicada", "promesa_procesada", "reclamo_procesado"}

    if current_domain and paso not in DOMINIOS_CERRADOS:
        # Conversación activa, mantener dominio salvo cambio explícito
        ultimo_msg = _last_human(state["messages"])
        if current_domain == "retention" and _detectar_billing(ultimo_msg):
            pass  # Cambió a billing
        else:
            return {}  # Sin cambios en el estado

    # Primera vez o dominio cerrado: clasificar
    nuevo_domain = _classify_domain(state["messages"])
    return {"domain": nuevo_domain}


def _classify_domain(messages: list) -> str:
    from llm.bedrock_client import BedrockClient
    client = BedrockClient()
    ultimo = _last_human(messages)
    prompt = ROUTE_PROMPT.format(input_text=ultimo)
    resp = client.invoke_simple(prompt)
    text = resp.strip().lower()
    if "retention" in text:
        return "retention"
    if "promise" in text:
        return "promise"
    if "billing" in text:
        return "billing"
    return "general"
```

### 2.2 Integración con MCP

#### Inicialización del cliente MCP

El cliente MCP es **async** y se inicializa una vez al arranque del consumer. El context manager `streamablehttp_client` mantiene la sesión HTTP viva.

```python
# mcp/client.py

import asyncio
import logging
from mcp import ClientSession
from mcp.client.streamable_http import streamablehttp_client

logger = logging.getLogger(__name__)

_session: ClientSession | None = None
_tools_cache: list | None = None
_tools_bedrock_cache: list | None = None

MCP_URL = None  # Se inicializa desde config


async def _connect(url: str):
    global _session, _tools_cache, _tools_bedrock_cache
    async with streamablehttp_client(url) as (read, write, _):
        async with ClientSession(read, write) as session:
            await session.initialize()
            _session = session
            result = await session.list_tools()
            _tools_cache = result.tools
            _tools_bedrock_cache = _convert_to_bedrock_format(_tools_cache)
            logger.info("MCP conectado. Tools disponibles: %s", len(_tools_cache))
            # Mantener la conexión viva mientras el consumer corra
            await asyncio.Future()  # bloquea para siempre


def initialize_mcp(mcp_url: str):
    """Lanza la conexión MCP en un thread de event loop separado."""
    global MCP_URL
    MCP_URL = mcp_url
    loop = asyncio.new_event_loop()
    import threading
    t = threading.Thread(target=lambda: loop.run_until_complete(_connect(mcp_url)), daemon=True)
    t.start()
    # Esperar a que los tools estén disponibles (máx 10s)
    import time
    for _ in range(100):
        if _tools_bedrock_cache is not None:
            break
        time.sleep(0.1)
    logger.info("MCP inicializado con %d tools", len(_tools_bedrock_cache or []))


def get_tools_for_bedrock() -> list:
    """Retorna los tools en formato Bedrock toolConfig.tools."""
    return _tools_bedrock_cache or []


def get_tools_by_domain(domain: str) -> list:
    """
    Filtra los tools MCP según el dominio.
    Esto permite que cada nodo agente solo vea tools relevantes.
    """
    ALL_TOOLS = get_tools_for_bedrock()
    BILLING_TOOLS = {
        "get_customer_info", "get_customer_service", "billing_info",
        "billing_lookup", "create_ticket", "run_network_diagnostic",
        "list_network_diagnostics", "get_latest_network_diagnostic",
        "request_connection_reset", "grant_mobile_topup",
        "list_available_offerings",
    }
    RETENTION_TOOLS = {
        "get_customer_info", "get_customer_service", "get_retention_tiers",
        "get_retention_eligibility", "get_retention_preview",
        "apply_retention_agreement",
    }
    PROMISE_TOOLS = {
        "get_customer_info", "billing_info", "payment_promises",
    }
    DOMAIN_FILTER = {
        "billing":   BILLING_TOOLS,
        "retention": RETENTION_TOOLS,
        "promise":   PROMISE_TOOLS,
        "general":   {t["toolSpec"]["name"] for t in ALL_TOOLS},
    }
    allowed = DOMAIN_FILTER.get(domain, set())
    return [t for t in ALL_TOOLS if t["toolSpec"]["name"] in allowed]


def _convert_to_bedrock_format(mcp_tools: list) -> list:
    """
    Convierte objetos Tool del MCP SDK al formato toolConfig.tools de Bedrock.
    Ref: https://docs.aws.amazon.com/nova/latest/userguide/tool-use-definition.html
    """
    result = []
    for tool in mcp_tools:
        result.append({
            "toolSpec": {
                "name": tool.name,
                "description": tool.description or "",
                "inputSchema": {"json": tool.inputSchema},
            }
        })
    return result


async def call_tool_async(tool_name: str, arguments: dict) -> str:
    """Ejecuta un tool MCP y retorna el resultado como string."""
    if _session is None:
        raise RuntimeError("MCP session no inicializada")
    result = await _session.call_tool(tool_name, arguments)
    # result.content es lista de TextContent/otros
    parts = []
    for block in result.content:
        if hasattr(block, "text"):
            parts.append(block.text)
        else:
            parts.append(str(block))
    return "\n".join(parts)


def call_tool_sync(tool_name: str, arguments: dict) -> str:
    """Versión síncrona para llamar desde nodos LangGraph síncronos."""
    loop = asyncio.new_event_loop()
    try:
        return loop.run_until_complete(call_tool_async(tool_name, arguments))
    finally:
        loop.close()
```

#### Nota sobre la arquitectura async/sync

LangGraph puede ejecutar grafos síncronos o async. El consumer actual es síncrono. La estrategia más simple es mantener los nodos del grafo como funciones síncronas y llamar a `call_tool_sync()` (que abre un nuevo event loop por llamada). Para mayor rendimiento en producción, se puede migrar el consumer a `async/await` y usar `call_tool_async()` directamente.

### 2.3 LLM via boto3/Bedrock

#### Reemplazo de `langchain-aws`

`langchain-aws.ChatBedrockConverse` se reemplaza con `boto3.client("bedrock-runtime").converse()`. El formato de la API es idéntico en ambos casos (es la misma API de AWS), pero ahora sin la capa de abstracción de LangChain.

**Formato exacto de la API `bedrock-runtime.converse()` con tool_use:**

```python
# Llamada básica con tools
response = bedrock_client.converse(
    modelId="us.anthropic.claude-sonnet-4-5",
    messages=[
        {"role": "user", "content": [{"text": "¿Cuáles son las facturas del cliente 123?"}]}
    ],
    system=[
        {"text": "Sos Emma, asistente de telecomunicaciones..."}
    ],
    inferenceConfig={
        "maxTokens": 4096,
        "temperature": 0,
    },
    toolConfig={
        "tools": [
            {
                "toolSpec": {
                    "name": "billing_info",
                    "description": "Facturas del cliente",
                    "inputSchema": {
                        "json": {
                            "type": "object",
                            "properties": {
                                "customer_id": {"type": "integer", "description": "ID del cliente"}
                            },
                            "required": ["customer_id"]
                        }
                    }
                }
            }
        ]
    }
)

# Respuesta
stop_reason = response["stopReason"]  # "end_turn" | "tool_use" | "max_tokens"
output_message = response["output"]["message"]
# output_message = {"role": "assistant", "content": [...]}
# content puede contener: {"text": "..."} y/o {"toolUse": {"toolUseId": "...", "name": "...", "input": {...}}}
```

**Loop completo de tool-use:**

```python
# Si stopReason == "tool_use":
for block in output_message["content"]:
    if "toolUse" in block:
        tool_call = block["toolUse"]
        tool_result_text = call_tool_sync(tool_call["name"], tool_call["input"])
        # Agregar tool_result al historial:
        messages.append({
            "role": "user",
            "content": [{
                "toolResult": {
                    "toolUseId": tool_call["toolUseId"],
                    "content": [{"text": tool_result_text}],
                    "status": "success"
                }
            }]
        })

# Volver a llamar al LLM con el historial actualizado
```

**Cliente completo (`llm/bedrock_client.py`):**

```python
import os
import boto3
import logging
from typing import Any

logger = logging.getLogger(__name__)


class BedrockClient:
    """
    Cliente boto3 directo para AWS Bedrock Converse API.
    Reemplaza langchain-aws.ChatBedrockConverse sin dependencia de LangChain.
    """

    def __init__(self):
        self.client = boto3.client(
            "bedrock-runtime",
            region_name=os.getenv("AWS_REGION", "us-east-1"),
        )
        self.model_brain = os.getenv("AWS_SECOND_LLM", "us.anthropic.claude-sonnet-4-5")
        self.model_haiku = os.getenv("AWS_PRIMARY_LLM", "anthropic.claude-3-haiku-20240307-v1:0")

    def invoke_simple(self, prompt: str, model: str | None = None) -> str:
        """
        Llamada simple sin tools. Retorna el texto de la respuesta.
        Usada para triage, routing y routers intermedios.
        """
        model_id = model or self.model_haiku
        response = self.client.converse(
            modelId=model_id,
            messages=[{"role": "user", "content": [{"text": prompt}]}],
            inferenceConfig={"maxTokens": 200, "temperature": 0},
        )
        content = response["output"]["message"]["content"]
        return "".join(b["text"] for b in content if "text" in b)

    def invoke_with_tools(
        self,
        messages: list[dict],
        system_prompt: str,
        tools: list[dict],
        model: str | None = None,
    ) -> dict:
        """
        Llamada con tools. Retorna la respuesta completa de Bedrock.
        El caller decide si hay tool_use y ejecuta el loop.
        """
        model_id = model or self.model_brain
        kwargs: dict[str, Any] = {
            "modelId":        model_id,
            "messages":       messages,
            "system":         [{"text": system_prompt}],
            "inferenceConfig": {"maxTokens": 4096, "temperature": 0},
        }
        if tools:
            kwargs["toolConfig"] = {"tools": tools}

        response = self.client.converse(**kwargs)
        return response

    def run_agent_loop(
        self,
        messages: list[dict],
        system_prompt: str,
        tools: list[dict],
        tool_executor,  # callable: (name, input) -> str
        max_iterations: int = 10,
    ) -> tuple[list[dict], str]:
        """
        Ejecuta el loop completo de tool-use hasta que el modelo responda
        con end_turn o se alcance el límite de iteraciones.

        Returns:
            (messages_history, final_text_response)
        """
        current_messages = list(messages)

        for iteration in range(max_iterations):
            response = self.invoke_with_tools(current_messages, system_prompt, tools)
            stop_reason = response["stopReason"]
            output_message = response["output"]["message"]

            # Agregar respuesta del asistente al historial
            current_messages.append(output_message)

            if stop_reason == "end_turn":
                # Extraer texto final
                final_text = "".join(
                    b["text"] for b in output_message["content"] if "text" in b
                )
                logger.info("[Bedrock] end_turn en iteración %d", iteration)
                return current_messages, final_text

            if stop_reason == "tool_use":
                tool_results = []
                for block in output_message["content"]:
                    if "toolUse" not in block:
                        continue
                    tool_call = block["toolUse"]
                    tool_name = tool_call["name"]
                    tool_input = tool_call["input"]
                    tool_use_id = tool_call["toolUseId"]

                    logger.info("[Bedrock] tool_use: %s(%s)", tool_name, tool_input)
                    try:
                        result_text = tool_executor(tool_name, tool_input)
                        tool_results.append({
                            "toolResult": {
                                "toolUseId": tool_use_id,
                                "content":   [{"text": result_text}],
                                "status":    "success",
                            }
                        })
                    except Exception as e:
                        logger.error("[Bedrock] tool %s failed: %s", tool_name, e)
                        tool_results.append({
                            "toolResult": {
                                "toolUseId": tool_use_id,
                                "content":   [{"text": f"Error: {e}"}],
                                "status":    "error",
                            }
                        })

                # Agregar resultados al historial para la próxima iteración
                current_messages.append({"role": "user", "content": tool_results})
                continue

            # stop_reason inesperado (max_tokens, guardrail, etc.)
            logger.warning("[Bedrock] stop_reason inesperado: %s", stop_reason)
            partial_text = "".join(
                b["text"] for b in output_message["content"] if "text" in b
            )
            return current_messages, partial_text or "Sin respuesta."

        logger.error("[Bedrock] Se alcanzó max_iterations=%d", max_iterations)
        return current_messages, "No pude completar la tarea. Intentá de nuevo."
```

### 2.4 Estado del grafo (StateGraph)

El nuevo estado unifica los tres TypedDict en uno solo, eliminando la dependencia de `langchain_core.messages.BaseMessage`. En la nueva arquitectura, `messages` es una lista de dicts nativas de Python con el formato de Bedrock Converse: `{"role": "user"|"assistant", "content": [...]}`.

```python
# graph/state.py

from typing import TypedDict, Optional, Annotated
from operator import add


def _merge_messages(existing: list, new: list) -> list:
    """
    Reducer para messages: acumula mensajes.
    Equivalente funcional de langchain_core.messages.add_messages
    pero con dicts nativos de Bedrock.
    """
    return existing + new


class AgentState(TypedDict):
    # ── Identidad ─────────────────────────────────────────────────────────────
    customer_id: str          # siempre string (Kafka envía strings)

    # ── Routing ───────────────────────────────────────────────────────────────
    domain: str               # "billing" | "retention" | "promise" | "general"
    paso_actual: str          # estado interno del flow activo

    # ── Mensajes (formato nativo Bedrock Converse) ────────────────────────────
    # {"role": "user"|"assistant", "content": [{"text": "..."} | {"toolUse": ...} | {"toolResult": ...}]}
    messages: Annotated[list[dict], _merge_messages]

    # ── Datos de dominio (cacheados para evitar re-fetches) ───────────────────
    cliente: Optional[dict]           # get_customer_info
    facturas: Optional[list]          # billing_info
    servicios: Optional[list]         # get_customer_service
    eligibility: Optional[dict]       # get_retention_eligibility
    ofertas_preview: Optional[list]   # get_retention_preview

    # ── Contexto de respuesta ─────────────────────────────────────────────────
    ticket_id: Optional[str]
    ultima_respuesta: str             # texto final para extraer en consumer
```

**Compatibilidad con checkpointer:** El checkpointer Redis/Postgres serializa el estado como JSON. Los dicts nativos de Python son más fáciles de serializar que los objetos `BaseMessage` de LangChain, lo que reduce la dependencia transitiva en `langchain-core` para la serialización del estado.

⚠️ **Nota importante sobre el checkpointer:** `langgraph-checkpoint-redis >= 0.2.0` sigue requiriendo `langchain-core >= 1.0.5` como dependencia transitiva (confirmado en `MIGRATION_0.2.0.md`). Sin embargo, esto NO implica usar las abstracciones de LangChain en el código propio — solo que el paquete del checkpointer las usa internamente. Se puede tener `langchain-core` instalado como dependencia sin importarla en el código del proyecto.

### 2.5 Nodo agente con loop tool-use

```python
# graph/nodes.py (fragmento del nodo billing)

import logging
from graph.state import AgentState
from llm.bedrock_client import BedrockClient
from mcp.client import get_tools_by_domain, call_tool_sync
from context_llm.contexts import get_system_prompt

logger = logging.getLogger(__name__)
_bedrock = BedrockClient()


def billing_agent_node(state: AgentState) -> dict:
    """
    Nodo agente de billing. El LLM decide autónomamente qué tools invocar.
    No hay routing interno — todo lo maneja el loop de tool-use de Bedrock.
    """
    customer_id = state["customer_id"]
    tools = get_tools_by_domain("billing")
    system = get_system_prompt("billing", customer_id)

    updated_messages, final_text = _bedrock.run_agent_loop(
        messages=state["messages"],
        system_prompt=system,
        tools=tools,
        tool_executor=call_tool_sync,
    )

    # Detectar si la conversación debe cerrarse (el LLM marca cierre en el texto)
    paso = _detect_paso(final_text, state.get("paso_actual", ""))

    return {
        "messages":       updated_messages[len(state["messages"]):],  # solo nuevos
        "ultima_respuesta": final_text,
        "paso_actual":    paso,
    }


def _detect_paso(text: str, current_paso: str) -> str:
    """Lógica simple de detección de cierre. El LLM puede incluir marcadores en el texto."""
    # En la nueva arquitectura, el LLM gestiona todo autónomamente.
    # Sólo marcamos "cerrado" cuando el texto indica fin de flujo.
    text_lower = text.lower()
    if any(kw in text_lower for kw in ["¿necesitás algo más?", "¿hay algo más", "con gusto te ayudo"]):
        return "respondido"
    return current_paso or "en_progreso"
```

### 2.6 Memoria y checkpointing

**Qué cambia:**
- Se elimina la importación de `langchain_core.messages.BaseMessage`, `AIMessage`, `HumanMessage` de `memory/memory_brain.py`.
- La función `get_memory()` ya no necesita convertir filas de Postgres a objetos LangChain; devuelve dicts nativos.
- El checkpointer Redis sigue funcionando igual: `RedisSaver.from_conn_string()` con `__enter__()` y `setup()`.

**Qué NO cambia:**
- La estructura de Redis (db=0 para checkpointer, db=1 para ruteo).
- La tabla `conversation_history` en Postgres.
- `get_checkpointer()`, `close_checkpointer()`, `get_full_conversation()`, `save_conversation()` — solo cambia el tipo de objetos (dicts vs BaseMessage).

**Nueva `memory/memory_brain.py` (cambios mínimos):**

```python
# Solo cambia la función _row_to_message:

def _row_to_message(rol: str | None, contenido: str | None) -> dict:
    """Convierte fila de Postgres a dict nativo Bedrock Converse."""
    r = (rol or "").lower().strip()
    role = "user" if r in ("human", "user", "humanmessage") else "assistant"
    return {"role": role, "content": [{"text": contenido or ""}]}

# Y get_full_conversation devuelve list[dict] en vez de list[BaseMessage]
# El checkpointer guarda el estado serializado; el formato de messages
# cambia de BaseMessage a dict, pero la serialización JSON funciona igual.
```

---

## Sección 3: Estructura de Archivos Nueva

```
ia-master-brain/
│
├── kafka/
│   └── consumer.py          # Consumer Kafka actualizado: sin LangChain,
│                            # inicializa MCPClient y BedrockClient al arranque
│
├── graph/
│   ├── __init__.py
│   ├── agent_graph.py       # Grafo LangGraph unificado (StateGraph + nodos)
│   ├── state.py             # AgentState TypedDict con dicts nativos Bedrock
│   └── nodes.py             # routing_node + billing/retention/promise/general agents
│
├── llm/
│   ├── __init__.py
│   └── bedrock_client.py    # Cliente boto3 con run_agent_loop() completo
│
├── mcp/
│   ├── __init__.py
│   └── client.py            # Inicialización streamablehttp_client + conversión Bedrock
│
├── memory/
│   ├── __init__.py
│   ├── memory_brain.py      # Checkpointer Redis/MemorySaver (sin LangChain messages)
│   ├── query.py             # Queries SQL sin cambios
│   └── save_to_db.py        # Conexión psycopg3 sin cambios
│
├── context_llm/
│   ├── __init__.py
│   └── contexts.py          # Prompts de sistema (refactorizados a funciones plain)
│
├── Dockerfile               # Sin cambios salvo actualizar CMD si cambia entry point
└── requirements.txt         # Actualizado (ver Sección 4)
```

**Módulos eliminados:**
- `agents/` → reemplazado por `graph/`
- `tools/` → reemplazado por MCP tools del back Java
- `flows/` → lógica absorbida por `graph/nodes.py`
- `connection_llm/` → reemplazado por `llm/bedrock_client.py`

---

## Sección 4: requirements.txt Nuevo

### Qué se saca
```
# ELIMINAR (dependencias de LangChain):
langchain>=0.3.0
langchain-core>=0.3.0
langchain-aws>=0.2.0
```

### Qué se mantiene
```
# MANTENER sin cambios:
langgraph>=0.2.0              # Orquestador del grafo
boto3>=1.34.0                 # Cliente AWS (ahora de uso directo para Bedrock)
confluent-kafka>=2.3.0        # Consumer Kafka
python-dotenv>=1.0.1          # Variables de entorno
pydantic>=2.0,<3              # Validación de datos (opcional pero útil)
redis>=5.0.0                  # Cliente Redis para ruteo (db=1)
psycopg[binary]>=3.1.0        # Conexión Postgres (ya existe como psycopg)
```

### Qué se agrega
```
# AGREGAR:
mcp>=1.0.0                   # Python MCP SDK (cliente Streamable-HTTP)
httpx>=0.27.0                # HTTP client async usado internamente por mcp SDK
anyio>=4.0.0                 # Runtime async compatible con mcp SDK
```

### Sobre langgraph-checkpoint-redis y langchain-core

`langgraph-checkpoint-redis >= 0.2.0` requiere `langchain-core >= 1.0.5` como dependencia transitiva. Hay dos opciones:

**Opción A (recomendada):** Mantener `langgraph-checkpoint-redis` y aceptar que `langchain-core` esté instalado como dependencia transitiva (sin importarlo en el código propio).
```
langgraph-checkpoint-redis>=0.2.0   # Arrastra langchain-core >= 1.0.5 transitivamente
langgraph-checkpoint-postgres>=3.0.4 # Idem
```

**Opción B (máxima independencia):** Reemplazar el checkpointer con `langgraph-checkpoint-postgres` como checkpointer primario (Postgres no tiene dependencia de LangChain en versiones recientes, verificar en PyPI antes de adoptar), o implementar un checkpointer Redis custom usando `langgraph-checkpoint` base. Esta opción agrega complejidad innecesaria para los beneficios que da.

**Recomendación:** Opción A. El objetivo es eliminar el USO de LangChain en el código, no necesariamente de `pip install`. `langchain-core` instalado como dep transitiva no agrega acoplamiento al código.

### requirements.txt completo propuesto
```txt
# Orquestación
langgraph>=1.0.0
langgraph-checkpoint-redis>=0.2.0
langgraph-checkpoint-postgres>=3.0.4

# LLM (Bedrock directo, sin langchain-aws)
boto3>=1.34.0

# MCP Client
mcp>=1.0.0
httpx>=0.27.0
anyio>=4.0.0

# Kafka
confluent-kafka>=2.3.0

# Bases de datos
psycopg[binary]>=3.1.0
redis>=5.0.0

# Utilidades
python-dotenv>=1.0.1
pydantic>=2.0,<3

# Transitiva necesaria para checkpointers (se instala automáticamente con langgraph-checkpoint-redis)
# langchain-core>=1.0.5  ← no declarar explícitamente, dejar que el checkpointer la jale
```

---

## Sección 5: Plan de Migración

### 5.1 Orden de cambios por archivo

El orden respeta las dependencias: primero las capas sin dependencias del dominio (llm, mcp), luego el estado, luego los nodos, luego el grafo, finalmente el entry point.

1. **`requirements.txt`** — Actualizar dependencias. Verificar instalación antes de tocar código.

2. **`llm/bedrock_client.py`** (nuevo) — Implementar `BedrockClient` con `invoke_simple()`, `invoke_with_tools()`, `run_agent_loop()`. Sin dependencias del resto del proyecto.

3. **`mcp/client.py`** (nuevo) — Implementar `MCPClient` con `initialize_mcp()`, `get_tools_by_domain()`, `call_tool_sync()`. Solo depende de `mcp` SDK y `llm/bedrock_client.py` no.

4. **`graph/state.py`** (nuevo) — Definir `AgentState` TypedDict con reducer `_merge_messages`. Sin dependencias de LangChain.

5. **`context_llm/contexts.py`** (modificar) — Agregar función `get_system_prompt(domain, customer_id)` que encapsula todos los prompts. Mantener las funciones existentes hasta completar la migración.

6. **`memory/memory_brain.py`** (modificar) — Cambiar `_row_to_message()` para retornar dicts. Cambiar `get_full_conversation()` para retornar `list[dict]`. Eliminar imports de `langchain_core.messages`.

7. **`graph/nodes.py`** (nuevo) — Implementar todos los nodos: `routing_node`, `billing_agent_node`, `retention_agent_node`, `promise_agent_node`, `general_agent_node`. Depende de `llm/`, `mcp/`, `graph/state.py`, `context_llm/`.

8. **`graph/agent_graph.py`** (nuevo) — Implementar `build_agent_graph(checkpointer)`. Depende de `graph/nodes.py` y `graph/state.py`.

9. **`kafka/consumer.py`** (modificar) — Reemplazar `LlmBrain` por `AgentGraph`. Eliminar imports de LangChain. Inicializar `MCPClient` y `BedrockClient`. Ver código completo en Sección 6.

10. **Eliminar módulos obsoletos** (después de verificar que el nuevo flujo funciona):
    - `agents/llm_brain.py`
    - `tools/tools.py` y `tools/__init__.py`
    - `flows/billings/`, `flows/retention/`, `flows/promise/`, `flows/new_service/`
    - `connection_llm/llm_conecction.py`

### 5.2 Qué NO cambia

| Componente | Estado |
|------------|--------|
| Kafka (topics, consumer group) | Sin cambios |
| Redis (estructura de keys de ruteo db=1) | Sin cambios |
| Checkpointer Redis (db=0, RedisSaver) | Sin cambios de API |
| Postgres `conversation-db` (esquema) | Sin cambios |
| Postgres `internet-db` (back Java) | Sin cambios |
| `docker-compose.yml` | Agregar `MCP_URL` env var para `ia_brain` |
| `.env.example` | Agregar `MCP_URL=http://localhost:8080/mcp` |
| Variables de entorno existentes | Sin cambios |
| Dockerfile | Sin cambios (salvo CMD si cambia el entry point) |
| Back Java `cliente_back` | Agregar Spring AI MCP Server (ver nota abajo) |
| Front `moustro-front` | Sin cambios |

### 5.3 Cambios en el back Java (cliente_back)

El back Java necesita agregar la dependencia de Spring AI MCP Server y exponer los 16 tools como MCP tools. Esto es trabajo separado del brain. Los endpoints REST existentes **no se eliminan** hasta que el MCP esté en producción y verificado.

Dependencias a agregar en `pom.xml`:
```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-mcp-server-spring-boot-starter</artifactId>
</dependency>
```

Endpoint que queda disponible: `POST /mcp` (Streamable HTTP, JSON-RPC 2.0).

### 5.4 Riesgos específicos

| Riesgo | Probabilidad | Mitigación |
|--------|-------------|------------|
| `mcp.client.streamable_http` API cambia entre versiones 1.x | Media | Pinear versión exacta: `mcp==1.9.4` (o la más reciente estable). |
| Loop async dentro del consumer síncrono causa deadlock | Alta | Usar `threading.Thread` con event loop separado como se describe en `mcp/client.py`, o migrar el consumer a async completo. |
| Formato de `tool.inputSchema` del MCP SDK no es JSON serializable directo | Media | El campo `inputSchema` de los Tool objects del MCP SDK en algunos casos viene como `dict`, en otros como objeto. Verificar con `tool.inputSchema.model_dump()` si es Pydantic. |
| Estado serializado en Redis cambia formato (dict vs BaseMessage) | Alta | **Cutover limpio:** hacer `FLUSHDB` del Redis db=0 al desplegar la nueva versión, ya que los checkpoints existentes con BaseMessage no son compatibles con los nuevos dicts. |
| `langgraph-checkpoint-redis 0.2.0` requiere `langchain-core >= 1.0.5` | Confirmado | Aceptar la dep transitiva (Opción A). |
| Spring AI pom.xml: versión 4.0.3 de Spring Boot puede no tener Spring AI stable | Media | Verificar Spring AI 1.0.0+ compatible con Spring Boot 3.x. El pom.xml actual usa Spring Boot 4.0.3, que puede requerir Spring AI 2.0.x. |

### 5.5 Estrategia de cutover

1. **Rama de feature:** Implementar toda la migración en una rama separada `feature/mcp-architecture`.
2. **Test local con MCP mock:** Antes de que el back Java esté listo, usar un servidor MCP mock (script Python con `FastMCP`) que sirva las mismas tools con datos hardcodeados.
3. **Variable de feature flag:** En `consumer.py`, tener `USE_MCP = os.getenv("USE_MCP", "false") == "true"` para poder hacer A/B entre la implementación vieja y la nueva en el mismo deploy.
4. **Migración de datos Redis:** Al hacer cutover, limpiar los checkpoints de Redis db=0 (`redis-cli -n 0 FLUSHDB`) para evitar incompatibilidades de formato.
5. **Deploy simultáneo del back Java con MCP** antes de habilitar `USE_MCP=true` en el brain.

---

## Sección 6: Código Completo de Archivos Clave

### 6.1 `llm/bedrock_client.py`

*(Código completo incluido en Sección 2.3)*

### 6.2 `mcp/client.py`

*(Código completo incluido en Sección 2.2)*

### 6.3 `graph/state.py`

```python
# graph/state.py

from __future__ import annotations
from typing import TypedDict, Optional, Annotated


def _merge_messages(existing: list[dict], new: list[dict]) -> list[dict]:
    """
    Reducer acumulador para la lista de mensajes.
    Equivalente funcional a langchain_core.messages.add_messages
    pero operando sobre dicts nativos del formato Bedrock Converse.
    """
    if existing is None:
        return new or []
    return existing + (new or [])


class AgentState(TypedDict):
    """
    Estado unificado del grafo.

    messages: lista de dicts en formato Bedrock Converse:
        {"role": "user"|"assistant", "content": [block, ...]}
        donde block puede ser:
        - {"text": "..."}
        - {"toolUse": {"toolUseId": "...", "name": "...", "input": {...}}}
        - {"toolResult": {"toolUseId": "...", "content": [...], "status": "success"|"error"}}
    """

    # ── Identidad ─────────────────────────────────────────────────────────────
    customer_id: str

    # ── Routing ───────────────────────────────────────────────────────────────
    domain: str        # "billing" | "retention" | "promise" | "general" | ""
    paso_actual: str   # estado interno del flow: "en_progreso" | "cerrado" | etc.

    # ── Historial de mensajes ─────────────────────────────────────────────────
    messages: Annotated[list[dict], _merge_messages]

    # ── Datos de dominio cacheados ────────────────────────────────────────────
    cliente:        Optional[dict]
    facturas:       Optional[list]
    servicios:      Optional[list]
    eligibility:    Optional[dict]
    ofertas_preview: Optional[list]

    # ── Salida ────────────────────────────────────────────────────────────────
    ticket_id:        Optional[str]
    ultima_respuesta: str
```

### 6.4 `graph/agent_graph.py`

```python
# graph/agent_graph.py

from __future__ import annotations
import logging
from langgraph.graph import StateGraph, END
from graph.state import AgentState
from graph.nodes import (
    routing_node,
    billing_agent_node,
    retention_agent_node,
    promise_agent_node,
    general_agent_node,
)

logger = logging.getLogger(__name__)


def build_agent_graph(checkpointer):
    """
    Construye el grafo LangGraph principal sin dependencias de LangChain.

    Flujo:
      START → routing → [billing_agent | retention_agent | promise_agent | general_agent] → END

    El routing detecta el dominio y lo guarda en state["domain"].
    Cada nodo agente ejecuta el loop tool-use completo con boto3/Bedrock
    y los tools MCP del dominio correspondiente.
    """
    workflow = StateGraph(AgentState)

    workflow.add_node("routing",          routing_node)
    workflow.add_node("billing_agent",    billing_agent_node)
    workflow.add_node("retention_agent",  retention_agent_node)
    workflow.add_node("promise_agent",    promise_agent_node)
    workflow.add_node("general_agent",    general_agent_node)

    workflow.set_entry_point("routing")

    workflow.add_conditional_edges(
        "routing",
        _route_by_domain,
        {
            "billing":   "billing_agent",
            "retention": "retention_agent",
            "promise":   "promise_agent",
            "general":   "general_agent",
        },
    )

    workflow.add_edge("billing_agent",   END)
    workflow.add_edge("retention_agent", END)
    workflow.add_edge("promise_agent",   END)
    workflow.add_edge("general_agent",   END)

    graph = workflow.compile(checkpointer=checkpointer)
    logger.info("AgentGraph compilado con checkpointer: %s", type(checkpointer).__name__)
    return graph


def _route_by_domain(state: AgentState) -> str:
    domain = state.get("domain", "general")
    if domain not in ("billing", "retention", "promise", "general"):
        return "general"
    return domain
```

### 6.5 `graph/nodes.py`

```python
# graph/nodes.py

from __future__ import annotations
import logging
from graph.state import AgentState
from llm.bedrock_client import BedrockClient
from mcp.client import get_tools_by_domain, call_tool_sync

logger = logging.getLogger(__name__)
_bedrock = BedrockClient()  # Singleton; boto3 client es thread-safe


# ── Prompts de sistema ──────────────────────────────────────────────────────

_ROUTING_PROMPT = """Clasificá el mensaje del cliente en UNA categoría.

- 'retention': el cliente pide descuento, promoción, quiere bajar el precio, o amenaza con darse de baja.
- 'promise': el cliente quiere hacer una promesa de pago o confirmar que pagará en una fecha.
- 'billing': el mensaje menciona factura, pago, deuda, cargo, corte, internet, TV, telefonía, plan, servicio. Si hay CUALQUIER mención a estos temas, es billing.
- 'general': SOLO si el mensaje es un saludo puro sin ninguna referencia a cuenta o servicio.

REGLA: ante la duda entre billing y general, elegí billing.

Mensaje: "{input_text}"
Respondé SOLO con una palabra: retention / promise / billing / general"""

_BILLING_SYSTEM = """Sos Emma, asistente de facturación y soporte de telecomunicaciones.
El ID del cliente es {customer_id}. PROHIBIDO pedirle el número de cliente.
Tenés acceso a tools para consultar facturas, abrir tickets de reclamo, registrar promesas de pago,
consultar servicios, ejecutar diagnósticos de red y otorgar recargas móviles.
Resolvé todo sin derivar a otros canales. Respondé en español, tono amable y profesional.
Cuando la conversación esté resuelta, preguntá "¿Necesitás algo más?"."""

_RETENTION_SYSTEM = """Sos Emma, especialista en retención de clientes de telecomunicaciones.
El ID del cliente es {customer_id}. PROHIBIDO pedirle datos de identificación.
Tu objetivo es retener al cliente ofreciéndole descuentos en sus servicios.
Primero verificá elegibilidad, luego mostrá las opciones, negoció amablemente y aplicá el acuerdo
solo cuando el cliente acepte explícitamente. Respondé en español."""

_PROMISE_SYSTEM = """Sos Emma, asistente de pagos de telecomunicaciones.
El ID del cliente es {customer_id}.
Tu objetivo es registrar una promesa de pago sobre las facturas vencidas.
Consultá las facturas, explicá cuáles pueden tener promesa de pago y registrá la promesa
cuando el cliente confirme la fecha. Respondé en español."""

_GENERAL_SYSTEM = """Sos Emma, asistente de soporte de telecomunicaciones.
El ID del cliente es {customer_id}.
Respondé consultas generales sobre planes, cobertura, horarios o cualquier otro tema.
Si el cliente menciona un problema específico, usá las tools disponibles para ayudarlo.
Respondé en español, tono amable."""


# ── Nodo de routing ─────────────────────────────────────────────────────────

def routing_node(state: AgentState) -> dict:
    """
    Detecta el dominio del mensaje y actualiza state["domain"].
    Si ya hay un dominio activo y la conversación no está cerrada, lo mantiene.
    """
    current_domain = state.get("domain", "")
    paso = state.get("paso_actual", "")
    PASOS_CERRADOS = {"cerrado", "no_elegible", "sin_ofertas", "retencion_aplicada",
                      "promesa_procesada", "reclamo_procesado"}

    if current_domain and paso not in PASOS_CERRADOS:
        # Conversación activa: mantener dominio salvo cambio explícito a billing
        if current_domain == "retention":
            ultimo_texto = _last_human_text(state["messages"])
            if _is_billing(ultimo_texto):
                # El cliente cambió de tema a billing
                logger.info("[routing] Cambio retention→billing para customer %s", state["customer_id"])
                return {"domain": "billing", "paso_actual": "en_progreso"}
        logger.info("[routing] Manteniendo dominio '%s' para customer %s", current_domain, state["customer_id"])
        return {}  # Sin cambios

    # Primera vez o dominio cerrado
    ultimo_texto = _last_human_text(state["messages"])
    nuevo_domain = _classify_domain(ultimo_texto)
    logger.info("[routing] Dominio detectado: '%s' para customer %s", nuevo_domain, state["customer_id"])
    return {"domain": nuevo_domain, "paso_actual": "en_progreso"}


def _last_human_text(messages: list[dict]) -> str:
    for m in reversed(messages):
        if m.get("role") == "user":
            for block in m.get("content", []):
                if "text" in block:
                    return block["text"]
    return ""


def _is_billing(text: str) -> bool:
    keywords = ["factura", "pago", "cobr", "servicio", "internet", "plan", "cargo", "deuda"]
    t = text.lower()
    return any(kw in t for kw in keywords)


def _classify_domain(text: str) -> str:
    prompt = _ROUTING_PROMPT.format(input_text=text)
    result = _bedrock.invoke_simple(prompt).strip().lower()
    if "retention" in result:
        return "retention"
    if "promise" in result:
        return "promise"
    if "billing" in result:
        return "billing"
    return "general"


# ── Nodos agente ─────────────────────────────────────────────────────────────

def billing_agent_node(state: AgentState) -> dict:
    return _run_agent(state, "billing", _BILLING_SYSTEM)


def retention_agent_node(state: AgentState) -> dict:
    return _run_agent(state, "retention", _RETENTION_SYSTEM)


def promise_agent_node(state: AgentState) -> dict:
    return _run_agent(state, "promise", _PROMISE_SYSTEM)


def general_agent_node(state: AgentState) -> dict:
    return _run_agent(state, "general", _GENERAL_SYSTEM)


def _run_agent(state: AgentState, domain: str, system_template: str) -> dict:
    """
    Función genérica para ejecutar un nodo agente:
    1. Obtiene los tools MCP filtrados por dominio.
    2. Construye el system prompt con el customer_id.
    3. Llama a BedrockClient.run_agent_loop() con el historial de mensajes.
    4. Retorna los nuevos mensajes y la respuesta final.
    """
    customer_id = state["customer_id"]
    tools = get_tools_by_domain(domain)
    system = system_template.format(customer_id=customer_id)

    existing_count = len(state["messages"])
    updated_messages, final_text = _bedrock.run_agent_loop(
        messages=state["messages"],
        system_prompt=system,
        tools=tools,
        tool_executor=call_tool_sync,
    )

    # Solo los mensajes nuevos para el reducer _merge_messages
    new_messages = updated_messages[existing_count:]
    paso = _detect_paso(final_text)

    logger.info("[%s_agent] customer=%s paso=%s respuesta=%s chars",
                domain, customer_id, paso, len(final_text))

    return {
        "messages":         new_messages,
        "ultima_respuesta": final_text,
        "paso_actual":      paso,
    }


def _detect_paso(text: str) -> str:
    """Detecta si el flujo concluyó basándose en el texto de respuesta."""
    t = text.lower()
    cierre = ["¿necesitás algo más?", "¿hay algo más", "con gusto", "quedó registrad",
              "estamos a tu disposición", "que tengas un buen día"]
    if any(kw in t for kw in cierre):
        return "respondido"
    return "en_progreso"
```

### 6.6 `kafka/consumer.py` actualizado

```python
# kafka/consumer.py

import os
import re
import json
import logging
import traceback
import redis
from dotenv import load_dotenv
from confluent_kafka import Consumer, Producer

from llm.bedrock_client import BedrockClient
from graph.agent_graph import build_agent_graph
from mcp.client import initialize_mcp
from memory.memory_brain import get_checkpointer, close_checkpointer, get_memory, save_conversation

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)
load_dotenv()

# ── Inicialización del cliente MCP ────────────────────────────────────────────
MCP_URL = os.getenv("MCP_URL", "http://localhost:8080/mcp")
try:
    initialize_mcp(MCP_URL)
    logger.info("MCP client inicializado en %s", MCP_URL)
except Exception as e:
    logger.error("Error inicializando MCP client: %s", e)

# ── Inicialización del LLM Bedrock ────────────────────────────────────────────
try:
    bedrock = BedrockClient()
    logger.info("BedrockClient listo")
except Exception as e:
    logger.error("Error inicializando BedrockClient: %s", e)
    bedrock = None

# ── Inicialización del grafo ──────────────────────────────────────────────────
brain_graph = None
try:
    checkpointer = get_checkpointer()
    brain_graph = build_agent_graph(checkpointer)
    logger.info("AgentGraph listo")
except Exception as e:
    logger.error("Error inicializando AgentGraph: %s", e)

# ── Redis para ruteo (db=1) ───────────────────────────────────────────────────
_MEMORY_REDIS_URL = os.getenv("MEMORY_REDIS_URL", "redis://localhost:6379")
redis_route = redis.Redis.from_url(_MEMORY_REDIS_URL, db=1, decode_responses=True)
ROUTE_TTL = 60 * 60 * 24  # 24 horas

def get_route(customer_id: str) -> str | None:
    return redis_route.get(f"route:{customer_id}")

def set_route(customer_id: str, route: str) -> None:
    redis_route.setex(f"route:{customer_id}", ROUTE_TTL, route)

def delete_route(customer_id: str) -> None:
    redis_route.delete(f"route:{customer_id}")

# ── Kafka ─────────────────────────────────────────────────────────────────────
_KAFKA_BOOTSTRAP = os.getenv("KAFKA_BOOTSTRAP", "localhost:9092")
consumer = Consumer({
    'bootstrap.servers':  _KAFKA_BOOTSTRAP,
    'group.id':           'agente-primario-group',
    'auto.offset.reset':  'earliest',
    'enable.auto.commit': False,
})
producer = Producer({'bootstrap.servers': _KAFKA_BOOTSTRAP})
RESPUESTAS_TOPIC = 'respuestas.agente'
consumer.subscribe(['consultas.usuario'])

# ── Helpers ───────────────────────────────────────────────────────────────────

def _clean_response(text: str) -> str:
    if not text:
        return ""
    text = re.sub(r"<(function_calls|invoke)>.*?</\1>", "", text, flags=re.DOTALL | re.IGNORECASE)
    return re.sub(r"\n{3,}", "\n\n", text).strip()


def send_response(respuesta: str, customer_id: str, content: str, clasificacion: str) -> None:
    payload = json.dumps({
        "respuesta":          respuesta,
        "customer_id":        customer_id,
        "contenido_original": content,
        "clasificacion":      clasificacion,
    }, ensure_ascii=False)
    producer.produce(RESPUESTAS_TOPIC, value=payload.encode("utf-8"))
    producer.flush()


def triage(content: str) -> str:
    """Clasifica el mensaje en RECLAMO o CONSULTA usando Haiku via boto3."""
    if bedrock is None:
        return "ERROR"
    try:
        prompt = f"""Clasificá el mensaje como RECLAMO o CONSULTA.
RECLAMO: quejas, facturas, servicios caídos, pedir descuentos, problemas con el servicio.
CONSULTA: preguntas generales sobre planes, cobertura, horarios.
Respondé SOLO con una palabra.
Mensaje: {content}"""
        result = bedrock.invoke_simple(prompt)
        return result.strip().upper()
    except Exception as e:
        logger.error("Triage failed: %s", e)
        return "ERROR"


def responder_consulta(content: str) -> str:
    """Responde consultas generales sin datos del cliente."""
    if bedrock is None:
        return "No pude procesar tu consulta."
    try:
        prompt = f"""Sos un asistente de telecomunicaciones.
Respondé esta consulta general de forma clara y breve.
No tenés acceso a datos del cliente, solo información general.
Consulta: {content}"""
        return bedrock.invoke_simple(prompt).strip()
    except Exception as e:
        logger.error("Consulta simple failed: %s", e)
        return "No pude procesar tu consulta. Intentá de nuevo."


def procesar_brain(content: str, customer_id: str) -> tuple[str, bool]:
    """
    Ejecuta el grafo con el mensaje del cliente.
    Retorna (respuesta_texto, conversacion_cerrada).
    """
    if brain_graph is None:
        raise RuntimeError("AgentGraph no inicializado")

    config = {"configurable": {"thread_id": str(customer_id)}}

    # Recuperar memoria previa si es primer turno del checkpointer
    memory = get_memory(customer_id)

    # Construir el mensaje de usuario en formato Bedrock
    user_message = {"role": "user", "content": [{"text": content}]}
    initial_messages = (memory or []) + [user_message]

    result_state = brain_graph.invoke(
        {
            "messages":    initial_messages,
            "customer_id": str(customer_id),
            "domain":      "",
            "paso_actual": "",
            "ultima_respuesta": "",
        },
        config=config,
    )

    respuesta = _clean_response(result_state.get("ultima_respuesta", ""))
    if not respuesta:
        # Fallback: extraer último mensaje de assistant del historial
        for m in reversed(result_state.get("messages", [])):
            if m.get("role") == "assistant":
                for block in m.get("content", []):
                    if "text" in block and block["text"]:
                        respuesta = _clean_response(block["text"])
                        break
            if respuesta:
                break

    if not respuesta:
        respuesta = "No pude generar una respuesta. Revisá los logs."

    cerrada = result_state.get("paso_actual") in ("cerrado", "retencion_aplicada",
                                                    "promesa_procesada", "reclamo_procesado")

    try:
        save_conversation(str(customer_id))
    except Exception as e:
        logger.warning("No se pudo guardar la conversación: %s", e)

    return respuesta, cerrada


# ── Loop principal ────────────────────────────────────────────────────────────
logger.info("Consumer started.")

try:
    while True:
        msg = consumer.poll(1.0)
        if msg is None or msg.error():
            continue

        try:
            data        = json.loads(msg.value().decode("utf-8"))
            content     = data.get("contenido", data.get("content", ""))
            customer_id = str(data.get("customer_id") or data.get("id_cliente") or 1)

            logger.info("Mensaje: '%s' | ID: %s", content, customer_id)

            route = get_route(customer_id)
            logger.info("[ROUTE] customer=%s route=%s", customer_id, route)

            if route == "BRAIN":
                try:
                    respuesta, cerrada = procesar_brain(content, customer_id)
                    send_response(respuesta, customer_id, content, "RECLAMO")
                    if cerrada:
                        delete_route(customer_id)
                        logger.info("[ROUTE] Conversación cerrada ID %s", customer_id)
                except Exception as e:
                    logger.error("Error en brain: %s\n%s", e, traceback.format_exc())

            elif route == "CONSULTA":
                respuesta = responder_consulta(content)
                send_response(respuesta, customer_id, content, "CONSULTA")

            else:
                clasificacion = triage(content)
                logger.info("Triage: %s | ID: %s", clasificacion, customer_id)

                if clasificacion == "RECLAMO" and brain_graph:
                    set_route(customer_id, "BRAIN")
                    try:
                        respuesta, cerrada = procesar_brain(content, customer_id)
                        send_response(respuesta, customer_id, content, clasificacion)
                        if cerrada:
                            delete_route(customer_id)
                    except Exception as e:
                        logger.error("Error brain tras triage: %s", e)
                        send_response(f"Error interno: {e}", customer_id, content, clasificacion)

                elif clasificacion == "CONSULTA":
                    set_route(customer_id, "CONSULTA")
                    respuesta = responder_consulta(content)
                    send_response(respuesta, customer_id, content, clasificacion)

                else:
                    logger.warning("Clasificación inválida: %s", clasificacion)

            consumer.commit(message=msg)

        except Exception as e:
            logger.error("Error en loop: %s\n%s", e, traceback.format_exc())

except KeyboardInterrupt:
    logger.info("Stopping...")
finally:
    consumer.close()
    close_checkpointer()
```

---

## Resumen Ejecutivo

La migración es **viable y directa**. El núcleo del cambio es:

1. **`langchain-aws` → `boto3` directo**: La API de `bedrock-runtime.converse()` es exactamente la misma que usaba `ChatBedrockConverse` internamente. No hay sorpresas.

2. **`@tool` de LangChain → tools MCP vía Spring AI**: Los 16 tools actuales en `tools/tools.py` se convierten en tools expuestos por el back Java via Spring AI MCP Server. El Python SDK los descubre dinámicamente con `session.list_tools()`.

3. **`langchain_core.messages.BaseMessage` → dicts nativos Python**: El formato de mensajes de Bedrock Converse es `{"role": "user"|"assistant", "content": [...]}`. Más simple y sin dependencia de LangChain.

4. **Tres subgrafos → un grafo unificado**: El routing por dominio queda dentro del grafo como un nodo, eliminando la lógica dispersa en `LlmBrain.run()`.

5. **Checkpointers**: `langgraph-checkpoint-redis` arrastra `langchain-core` como dep transitiva, pero eso no implica usar sus abstracciones en el código. El estado se serializa como JSON nativo.

**Tiempo estimado de implementación:** 3-5 días para un desarrollador familiarizado con el código actual. El mayor riesgo es la sincronización async/sync del cliente MCP dentro del consumer Kafka síncrono.

---

*Content was rephrased for compliance with licensing restrictions.*  
*Sources consultados: [AWS Bedrock Converse API](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_Converse.html), [MCP Python SDK example](https://raw.githubusercontent.com/daveebbelaar/ai-cookbook/main/mcp/crash-course/3-simple-server-setup/client-streamable-http.py), [MCP+Bedrock integration](https://raw.githubusercontent.com/davidshtian/MCP-on-AWS-Bedrock/hand/client_stdio.py), [langgraph-checkpoint-redis migration guide](https://github.com/redis-developer/langgraph-redis/blob/main/MIGRATION_0.2.0.md), [Spring AI MCP Server](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-server-boot-starter-docs.html)*
