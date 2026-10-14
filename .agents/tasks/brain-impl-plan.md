# Plan de Implementación: Migración ia-master-brain a MCP + boto3

**Basado en:** `ia-brain-redesign.md` (1531 líneas) + `mcp-migration-design.md`  
**Fecha:** 2025  
**Rama objetivo:** `feature/mcp-architecture`

---

## 0. Contexto para el implementador

### Estado actual del código (lo que existe hoy)

| Archivo | Rol | Dependencia a eliminar |
|---------|-----|------------------------|
| `kafka/consumer.py` | Entry point, triage, routing Redis | `langchain_core.messages.HumanMessage`, `langchain_aws` (via `LlmBrain`) |
| `agents/llm_brain.py` | Orquestador de subgrafos, routing por intent | `langchain_core.messages.*`, `langchain_aws`, `ToolNode` prebuilt |
| `connection_llm/llm_conecction.py` | Wrapper `ChatBedrockConverse` | `langchain_aws` — módulo completo a eliminar |
| `tools/tools.py` + `tools/__init__.py` | 16 tools HTTP directos con `@tool` | `langchain.tools.tool` — módulo completo a eliminar |
| `flows/billings/nodes.py` | Nodos de billing | `langchain_core.messages.*`, imports de `tools/` |
| `flows/billings/routers.py` | Routers LangChain del subgrafo billing | `langchain_core.messages.*` |
| `flows/billings/graph.py` | Subgrafo billing | `langchain_core.messages.HumanMessage` |
| `flows/retention/nodes.py` | Nodos de retención | `langchain_core.messages.*`, imports de `tools/` |
| `flows/retention/graph.py` | Subgrafo retención | `langchain_core.messages.HumanMessage` |
| `flows/promise/nodes.py` | Nodos de promesa de pago | `langchain_core.messages.*`, imports de `tools/` |
| `flows/promise/graph.py` | Subgrafo promesa | `langchain_core.messages.HumanMessage` |
| `memory/memory_brain.py` | Checkpointer Redis/MemorySaver + Postgres | `langchain_core.messages.AIMessage`, `HumanMessage`, `BaseMessage` |
| `context_llm/contexts.py` | Prompts: `agent_facturacion`, `route_prompt` | Ninguna — solo funciones Python puras |
| `requirements.txt` | Deps en la raíz del repo | `langchain>=0.3.0`, `langchain-core>=0.3.0`, `langchain-aws>=0.2.0` |

### Nueva estructura de módulos a crear

```
ia-master-brain/
├── llm/
│   ├── __init__.py
│   └── bedrock_client.py       ← nuevo, reemplaza connection_llm/
├── mcp/
│   ├── __init__.py
│   └── client.py               ← nuevo, reemplaza tools/
├── graph/
│   ├── __init__.py
│   ├── state.py                ← nuevo AgentState sin LangChain
│   ├── nodes.py                ← nuevo, absorbe flows/ y agents/
│   └── agent_graph.py          ← nuevo, grafo unificado
├── memory/
│   └── memory_brain.py         ← modificar (quitar imports LangChain)
├── context_llm/
│   └── contexts.py             ← modificar (consolidar todos los prompts)
├── kafka/
│   └── consumer.py             ← modificar (quitar LangChain, inicializar MCP)
└── requirements.txt            ← modificar (quitar langchain*)
```

**Módulos a eliminar al final (no antes de verificar el nuevo flujo):**
- `agents/` (completo)
- `tools/` (completo)
- `flows/` (completo)
- `connection_llm/` (completo)

---

## 1. Estructura de archivos a crear/modificar

### 1.1 Archivos nuevos

| Archivo | Propósito |
|---------|-----------|
| `ia-master-brain/llm/__init__.py` | Paquete vacío |
| `ia-master-brain/llm/bedrock_client.py` | `BedrockClient`: `invoke_simple()`, `invoke_with_tools()`, `run_agent_loop()` |
| `ia-master-brain/mcp/__init__.py` | Paquete vacío |
| `ia-master-brain/mcp/client.py` | `initialize_mcp()`, `get_tools_by_domain()`, `call_tool_sync()`, `_convert_to_bedrock_format()` |
| `ia-master-brain/graph/__init__.py` | Paquete vacío |
| `ia-master-brain/graph/state.py` | `AgentState` TypedDict con `_merge_messages` reducer, sin LangChain |
| `ia-master-brain/graph/nodes.py` | `routing_node`, `billing_agent_node`, `retention_agent_node`, `promise_agent_node`, `general_agent_node`, `_run_agent()`, `_detect_paso()` |
| `ia-master-brain/graph/agent_graph.py` | `build_agent_graph(checkpointer)` → `StateGraph` compilado |

### 1.2 Archivos a modificar

| Archivo | Cambio |
|---------|--------|
| `requirements.txt` (raíz del repo) | Quitar `langchain*`, agregar `mcp>=1.0.0`, `httpx>=0.27.0`, `anyio>=4.0.0` |
| `ia-master-brain/context_llm/contexts.py` | Agregar `get_system_prompt(domain, customer_id)` + todos los prompts de `flows/` consolidados |
| `ia-master-brain/memory/memory_brain.py` | Cambiar `_row_to_message()` → retorna `dict` nativo, cambiar `get_full_conversation()` → retorna `list[dict]`, cambiar `save_conversation()` → serializa dicts, quitar imports `langchain_core.messages.*` |
| `ia-master-brain/kafka/consumer.py` | Quitar `LlmBrain`, `llm_master`, `langchain_core.messages.HumanMessage`; agregar `initialize_mcp()`, `BedrockClient`, `build_agent_graph()` |
| `.env.example` | Agregar `MCP_URL=http://localhost:8080/mcp` |
| `docker-compose.yml` | Agregar `MCP_URL: http://cliente-back:8080/mcp` en `ia_brain.environment`, quitar `BACK_API`, `BACK_RETENTION_API`, `BACK_SERVICE_API` |

---

## 2. Orden de creación (por dependencias entre módulos)

El grafo de dependencias del nuevo código es:

```
requirements.txt
    │
    ▼
llm/bedrock_client.py          ← sin deps del proyecto
    │
    ▼
mcp/client.py                  ← depende de mcp SDK
    │
    ▼
graph/state.py                 ← sin deps del proyecto
    │
    ▼
context_llm/contexts.py        ← sin deps del proyecto (solo strings)
    │
    ▼
memory/memory_brain.py         ← depende de langgraph checkpointers (sin LangChain messages)
    │
    ▼
graph/nodes.py                 ← depende de llm/, mcp/, graph/state.py, context_llm/
    │
    ▼
graph/agent_graph.py           ← depende de graph/nodes.py y graph/state.py
    │
    ▼
kafka/consumer.py              ← depende de todo lo anterior
    │
    ▼
(eliminar) agents/, tools/, flows/, connection_llm/
```

### Paso 1 — `requirements.txt`
Actualizar dependencias. El resto del plan asume que el entorno tiene las nuevas deps instaladas.

### Paso 2 — `llm/bedrock_client.py` (nuevo)
Capa de acceso a Bedrock sin LangChain. Sin dependencias del proyecto.

### Paso 3 — `mcp/client.py` (nuevo)
Inicialización del cliente MCP con thread dedicado + event loop separado. Expone `get_tools_by_domain()` y `call_tool_sync()`.

### Paso 4 — `graph/state.py` (nuevo)
Define `AgentState` TypedDict con `_merge_messages`. Sin dependencias de LangChain.

### Paso 5 — `context_llm/contexts.py` (modificar)
Consolidar todos los prompts de `flows/` en una sola función `get_system_prompt(domain, customer_id)`. Mantener las funciones existentes como aliases para no romper imports todavía.

### Paso 6 — `memory/memory_brain.py` (modificar)
Cambiar `_row_to_message()` y `get_full_conversation()` para trabajar con dicts nativos en lugar de objetos `BaseMessage`. Quitar todos los imports de `langchain_core.messages`.

### Paso 7 — `graph/nodes.py` (nuevo)
Todos los nodos del grafo unificado. Depende de pasos 2, 3, 4 y 5.

### Paso 8 — `graph/agent_graph.py` (nuevo)
Construye el `StateGraph`. Depende del paso 7.

### Paso 9 — `kafka/consumer.py` (modificar)
Reemplaza `LlmBrain` por `build_agent_graph()`. Inicializa MCP y Bedrock al arranque.

### Paso 10 — `.env.example` y `docker-compose.yml`
Agregar variables de entorno. No afecta al código Python.

### Paso 11 — Eliminar módulos obsoletos
Solo después de verificar que el nuevo flujo funciona end-to-end.

---

## 3. Puntos críticos de integración

### 3.1 Cómo `consumer.py` invoca el grafo (antes vs después)

**Antes:**
```python
# consumer.py invoca LlmBrain.run(content, customer_id)
# que internamente construye HumanMessage, llama subgrafos con LangChain objects
estado = brain.run(content, customer_id)
raw = _last_ai_content(estado.get("messages", []))  # extrae de AIMessage
```

**Después:**
```python
# consumer.py construye el mensaje Bedrock directamente
user_message = {"role": "user", "content": [{"text": content}]}
result_state = brain_graph.invoke(
    {"messages": [user_message], "customer_id": str(customer_id),
     "domain": "", "paso_actual": "", "ultima_respuesta": ""},
    config={"configurable": {"thread_id": str(customer_id)}},
)
respuesta = result_state.get("ultima_respuesta", "")
# Fallback si ultima_respuesta está vacía: iterar messages en reversa buscando role=="assistant"
```

**Punto crítico:** El `thread_id` del checkpointer ahora es siempre `str(customer_id)` sin prefijo de subgrafo. Los checkpoints anteriores (con prefijos `factura-`, `retention-`, `promise-`) quedarán huérfanos — requiere `FLUSHDB` en Redis db=0 en el momento del cutover.

### 3.2 Cómo el grafo llama MCP

**Flujo de una llamada MCP dentro de un nodo:**

```
graph/nodes.py::_run_agent()
    │
    ├── BedrockClient.run_agent_loop(messages, system_prompt, tools, tool_executor=call_tool_sync)
    │       │
    │       │ stopReason == "tool_use"
    │       ▼
    │   mcp/client.py::call_tool_sync(tool_name, tool_input)
    │       │
    │       ├── asyncio.new_event_loop().run_until_complete(call_tool_async(...))
    │       │       │
    │       │       └── _session.call_tool(tool_name, arguments)
    │       │               └── SSE/Streamable-HTTP → Spring AI MCP Server
    │       │                       └── @Tool Java method → DTO → JSON
    │       │
    │       └── retorna string (JSON del DTO)
    │
    └── retorna (updated_messages, final_text)
```

**Punto crítico:** `call_tool_sync()` abre un event loop nuevo por cada llamada. En production, con muchas llamadas simultáneas, esto puede ser lento. La alternativa es migrar el consumer a async — pero eso está fuera del scope de este plan. Para el scope actual, `asyncio.new_event_loop()` por llamada es la estrategia del diseño y es suficiente para el volumen esperado.

**Punto crítico:** La sesión MCP (`_session`) se inicializa en un thread daemon con su propio event loop via `initialize_mcp()` al arranque del consumer. El `call_tool_async()` que usa esa sesión debe correr en el MISMO event loop donde la sesión fue creada — de lo contrario falla con "Future attached to a different loop". La implementación de `mcp/client.py` del diseño resuelve esto compartiendo el loop a través de una variable global. El implementador DEBE verificar que `call_tool_async` se ejecuta en el loop correcto o usar `run_coroutine_threadsafe(loop)` en lugar de `asyncio.new_event_loop()`.

### 3.3 Cómo `memory_brain.py` retorna dicts nativos

**Antes:**
```python
def _row_to_message(rol, contenido) -> BaseMessage:
    # retorna HumanMessage(content=c) o AIMessage(content=c)

def get_full_conversation(customer_id) -> list[BaseMessage]:
    # retorna lista de objetos LangChain del checkpointer Redis
```

**Después:**
```python
def _row_to_message(rol, contenido) -> dict:
    role = "user" if rol in ("human", "user", "humanmessage") else "assistant"
    return {"role": role, "content": [{"text": contenido or ""}]}

def get_full_conversation(customer_id) -> list[dict]:
    # El checkpointer Redis guarda el estado serializado como JSON
    # Los mensajes en el nuevo AgentState son dicts, así que se recuperan como dicts
    # IMPORTANTE: los checkpoints OLD (con BaseMessage) son incompatibles
    # → limpiar Redis db=0 en el cutover (ver sección 5)
```

**Punto crítico:** `save_conversation()` actualmente llama `getattr(m, "type", None)` y `getattr(m, "content", "")` sobre objetos `BaseMessage`. Después de la migración, los mensajes son dicts: el acceso cambia a `m.get("role")` y `"\n".join(b.get("text","") for b in m.get("content",[]) if "text" in b)`.

### 3.4 Inicialización async de MCP dentro del consumer síncrono

El consumer Kafka es síncrono. El cliente MCP es async. La estrategia del diseño:

```python
# En consumer.py al inicio:
from mcp.client import initialize_mcp
MCP_URL = os.getenv("MCP_URL", "http://localhost:8080/mcp")
initialize_mcp(MCP_URL)  # Lanza un daemon thread con event loop dedicado
                         # Bloquea hasta que los tools estén disponibles (timeout 10s)
```

El thread daemon mantiene la conexión `streamablehttp_client` viva mediante `await asyncio.Future()`. Si el back Java está caído al arranque, `initialize_mcp()` falla silenciosamente (log de error) y `get_tools_by_domain()` retorna lista vacía, lo que hace que Bedrock responda sin tools — degradado pero sin crash.

### 3.5 Cambio de `thread_id` por dominio a `thread_id` único por cliente

**Antes:** `LlmBrain.run()` usaba thread_ids separados:
- `"factura-{customer_id}"` para billing
- `"retention-{customer_id}"` para retention  
- `"promise-{customer_id}"` para promise
- `"{customer_id}"` para general

**Después:** Un único `thread_id = str(customer_id)` para todos los dominios. El `AgentState` incluye `domain` y `paso_actual` que el `routing_node` lee para mantener la conversación activa entre turnos.

**Implicancia:** El routing no necesita checkear múltiples checkpoints — el estado completo (incluyendo `domain` y `paso_actual`) persiste en el único checkpoint del cliente.

### 3.6 Detección de cierre de conversación

**Antes:** `consumer.py` chequeaba `estado.get("paso_actual") == "cerrado"` en el resultado del subgrafo.

**Después:** El consumer chequea un conjunto de valores:
```python
PASOS_CERRADOS = {"cerrado", "retencion_aplicada", "promesa_procesada", "reclamo_procesado"}
cerrada = result_state.get("paso_actual") in PASOS_CERRADOS
```

El `routing_node` también lee este conjunto para detectar si la conversación cerró y hay que reiniciar el dominio.

---

## 4. System prompts a extraer de `flows/` para `context_llm/contexts.py`

Actualmente los prompts están dispersos en 4 archivos. Hay que consolidarlos en `context_llm/contexts.py` bajo la función `get_system_prompt(domain, customer_id)`.

### 4.1 Inventario completo de prompts actuales

| Archivo | Variable | Destino en `contexts.py` |
|---------|----------|--------------------------|
| `context_llm/contexts.py` | `agent_facturacion()` | Mantener como alias o fusionar en `_GENERAL_SYSTEM` |
| `context_llm/contexts.py` | `route_prompt()` | Reemplazar por `_ROUTING_PROMPT` del diseño |
| `flows/billings/prompts.py` | `EXPLICACION_FACTURA` | Fusionar en `_BILLING_SYSTEM` |
| `flows/billings/prompts.py` | `SYSTEM_RECLAMO` | Ya no hace falta: en la nueva arq. el LLM extrae datos autónomamente |
| `flows/billings/prompts.py` | `ROUTE_PRINCIPAL_PROMPT` | Ya no hace falta: absorbido por `_ROUTING_PROMPT` |
| `flows/billings/prompts.py` | `ROUTE_SERVICIOS_PROMPT` | Ya no hace falta: absorbido por `_ROUTING_PROMPT` |
| `flows/billings/prompts.py` | `EXPLICACION_SERVICIOS` | Fusionar en `_BILLING_SYSTEM` |
| `flows/retention/promps.py` | `PROMPT_NEGOCIACION` | Fusionar en `_RETENTION_SYSTEM` |
| `flows/retention/promps.py` | `ROUTE_NEGOCIACION` | Ya no hace falta: el LLM decide autónomamente |
| `flows/retention/promps.py` | `SYSTEM_EXTRAER_ACUERDO` | Ya no hace falta: el LLM aplica la retención cuando acepta |
| `flows/promise/promps.py` | `PROMPT_PROMISE_1` | Fusionar en `_PROMISE_SYSTEM` |
| `flows/promise/promps.py` | `ROUTE_SYSTEM_PROMICE` | Ya no hace falta |
| `flows/promise/promps.py` | `SYSTEM_PROMISE` | Ya no hace falta: el LLM registra la promesa con el tool directamente |

### 4.2 Prompts a implementar en `context_llm/contexts.py`

```
_ROUTING_PROMPT       ← del diseño (sección 6.5 de ia-brain-redesign.md)
_BILLING_SYSTEM       ← fusión de agent_facturacion + EXPLICACION_FACTURA
_RETENTION_SYSTEM     ← fusión con PROMPT_NEGOCIACION (reglas de negociación)
_PROMISE_SYSTEM       ← fusión con PROMPT_PROMISE_1 (reglas de promesa)
_GENERAL_SYSTEM       ← igual al del diseño
```

### 4.3 Reglas de fusión — qué conservar de cada prompt existente

**`_BILLING_SYSTEM`** debe incluir (de `EXPLICACION_FACTURA`):
- "Mostrá la factura ANTES de pedir datos" (regla de oro de UX)
- "No derivar a otros canales"
- "No inventar datos"
- La lógica de identificación de mes por ID de factura (FAC-2026-001=enero, etc.)
- La instrucción de preguntar por promesa de pago si la factura está impaga

**`_RETENTION_SYSTEM`** debe incluir (de `PROMPT_NEGOCIACION`):
- "Empezá con el nivel más BAJO"
- "Si rechaza, subí al siguiente nivel"
- "Si llegaste al máximo y rechaza, cerrá"
- Formato de presentación directo (sin headers, un bloque)
- Fórmula de cashback (calcular precio final con cashback)

**`_PROMISE_SYSTEM`** debe incluir (de `PROMPT_PROMISE_1`):
- "El plazo es SIEMPRE 48 horas, no se negocia"
- "Calculá vos la fecha, no le preguntes al cliente"
- El caso `puede_prometer = False` (más de 2 facturas vencidas)
- La info de ClaroPay con cashback 15% y el link

**Nota sobre `SYSTEM_RECLAMO`, `ROUTE_NEGOCIACION`, `SYSTEM_EXTRAER_ACUERDO`, `SYSTEM_PROMISE`:** Estos prompts eran necesarios porque el código anterior manejaba manualmente la extracción de datos con Haiku y los routers intermedios. En la nueva arquitectura, el LLM principal (Sonnet) tiene acceso directo a los tools y gestiona el flujo completo autónomamente. Estos prompts se vuelven obsoletos.

### 4.4 Nueva firma de `contexts.py`

```python
# Nueva función principal
def get_system_prompt(domain: str, customer_id: str) -> str:
    """Retorna el system prompt para el dominio y cliente dados."""
    ...

# Prompts de routing (usados por routing_node)
ROUTING_PROMPT: str = "..."

# Mantener como aliases para no romper imports durante transición
def agent_facturacion() -> str: ...  # retorna _BILLING_SYSTEM sin format
def route_prompt() -> str: ...       # retorna _ROUTING_PROMPT
```

---

## 5. Verificaciones de coherencia entre módulos

### 5.1 Nombres de tools MCP (Java → Python)

**Riesgo crítico:** Spring AI 2.0 usa el nombre del método Java como `name` del tool (camelCase por defecto). El `routing_node` y `mcp/client.py::get_tools_by_domain()` filtran por nombres en snake_case.

**Verificación necesaria antes de implementar Python:**
1. Levantar el back Java con Spring AI MCP configurado
2. Hacer un request `tools/list` al endpoint `POST /mcp/message`  
   Payload: `{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}`
3. Confirmar que los nombres retornados coinciden con los del filtro en `mcp/client.py`:
   ```
   get_customer_info, get_customer_service, billing_info, billing_lookup,
   create_ticket, payment_promises, grant_mobile_topup, request_connection_reset,
   run_network_diagnostic, list_network_diagnostics, get_latest_network_diagnostic,
   get_retention_tiers, get_retention_eligibility, get_retention_preview,
   apply_retention_agreement, list_available_offerings
   ```
4. Si los nombres no coinciden, agregar `name = "snake_case_name"` en cada `@Tool` de Java antes de continuar con Python.

### 5.2 Formato de `inputSchema` de tools MCP

Los objetos `Tool` del MCP Python SDK tienen `.inputSchema` que puede ser un `dict` o un objeto Pydantic. `_convert_to_bedrock_format()` en `mcp/client.py` hace:
```python
"inputSchema": {"json": tool.inputSchema}
```

Si `tool.inputSchema` es un objeto Pydantic, necesita `.model_dump()`. Verificar con:
```python
print(type(tools[0].inputSchema))
```
Y agregar el `model_dump()` condicional si es necesario.

### 5.3 Coherencia del estado entre `memory_brain.py` y `graph/state.py`

El checkpointer Redis guarda el estado serializado del grafo. El estado incluye el campo `messages: list[dict]`. Cuando `get_full_conversation()` lee los checkpoints viejos (que tienen `BaseMessage`), fallará la deserialización.

**Verificación:** Al hacer cutover, ejecutar en el host Docker:
```bash
docker exec redis_memory redis-cli -n 0 FLUSHDB
```
Esto limpia todos los checkpoints anteriores. Hacerlo ANTES de levantar la nueva versión del `ia_brain`.

### 5.4 Coherencia del formato de mensajes entre `memory_brain.py` y `consumer.py`

`get_memory()` retorna `list[dict]` (Bedrock format). `consumer.py` lo usa en:
```python
initial_messages = (memory or []) + [user_message]
```
donde `user_message = {"role": "user", "content": [{"text": content}]}`.

El `AgentState` acepta `list[dict]` en `messages` — coherente ✓

`save_conversation()` itera mensajes y hace:
```python
rol = getattr(m, "type", None) or m.__class__.__name__.lower()
contenido = getattr(m, "content", "")
```
**Esto se rompe con dicts.** Debe cambiarse a:
```python
rol = m.get("role", "user")  # "user" | "assistant"
# contenido: concatenar bloques de texto
contenido = " ".join(b.get("text","") for b in m.get("content",[]) if "text" in b)
```

### 5.5 Coherencia del `thread_id` entre turnos de la misma conversación

**Antes:** `billing` usaba `thread_id = f"factura-{customer_id}"`, `retention` usaba `f"retention-{customer_id}"`. El routing entre subgrafos era responsabilidad de `LlmBrain.run()`.

**Después:** Un único `thread_id = str(customer_id)`. El `routing_node` lee `state["domain"]` y `state["paso_actual"]` del checkpoint Redis para decidir si mantener o cambiar de dominio.

**Verificación de regresión:** En el segundo turno de una conversación de billing, el `routing_node` debe detectar `domain="billing"` y `paso_actual="en_progreso"` en el estado persistido, y retornar `{}` (sin cambios) para que `billing_agent_node` continúe sin re-clasificar.

### 5.6 Coherencia entre `_detect_paso()` en `graph/nodes.py` y `consumer.py`

`consumer.py` usa:
```python
cerrada = result_state.get("paso_actual") in ("cerrado", "retencion_aplicada",
                                               "promesa_procesada", "reclamo_procesado")
```

`_detect_paso()` en `graph/nodes.py` actualmente retorna `"respondido"` o `"en_progreso"`. Para que `consumer.py` detecte el cierre correctamente, los nodos agente deben retornar `"reclamo_procesado"` / `"retencion_aplicada"` / `"promesa_procesada"` cuando corresponda.

**Resolución:** Enriquecer `_detect_paso()` para detectar marcadores específicos de cada dominio en el texto de respuesta:
- Si contiene "Retención registrada" → `"retencion_aplicada"`
- Si contiene "promesa de pago quedó registrada" → `"promesa_procesada"`
- Si contiene "Reclamo registrado" → `"reclamo_procesado"`
- Si contiene "¿Necesitás algo más?" → `"respondido"` (conversación activa pero sin acción pendiente)

**O bien:** Pasar el `domain` a `_detect_paso(text, domain)` para contextualizar los marcadores.

### 5.7 Coherencia del `customer_id` entre tipos (str vs int)

El código actual es inconsistente: `billing` recibe `customer_id: str`, `retention` recibe `customer_id: int`. El `AgentState` nuevo lo define como `str` siempre.

Los tools MCP de Java esperan `customer_id: integer`. La conversión está en la descripción del tool (`"type": "integer"`) — Bedrock/MCP SDK hace el casting automáticamente. Sin embargo, los tests de integración deben confirmar que `"42"` se envía como `42` al tool Java.

**Verificación:** Al llamar `billing_info` via MCP con `{"customer_id": "42"}`, confirmar que el servidor Java recibe el entero `42` sin errores de tipo.

### 5.8 Coherencia de variables de entorno

| Variable | Usado en | Valor Docker | Valor local |
|----------|---------|--------------|-------------|
| `MCP_URL` | `mcp/client.py` | `http://cliente-back:8080/mcp` | `http://localhost:8080/mcp` |
| `AWS_REGION` | `llm/bedrock_client.py` | `${AWS_REGION:-us-east-1}` | en `.env` |
| `AWS_SECOND_LLM` | `llm/bedrock_client.py` | `${AWS_SECOND_LLM:-us.anthropic.claude-sonnet-4-6}` | en `.env` |
| `AWS_PRIMARY_LLM` | `llm/bedrock_client.py` | `${AWS_PRIMARY_LLM:-...haiku...}` | en `.env` |
| `BACK_API`, `BACK_RETENTION_API`, `BACK_SERVICE_API` | `tools/tools.py` (eliminar) | Eliminar de `docker-compose.yml` cuando se eliminen los tools |

---

## 6. Plan paso a paso con verificaciones

- [ ] **1. Actualizar `requirements.txt` (raíz)**
  
  Eliminar `langchain>=0.3.0`, `langchain-core>=0.3.0`, `langchain-aws>=0.2.0`.  
  Agregar `mcp>=1.0.0`, `httpx>=0.27.0`, `anyio>=4.0.0`.  
  Mantener todo lo demás exactamente igual.  
  Files: `/home/facuvega/Documents/agent-internet-ia/requirements.txt`  
  Verify: `pip install -r requirements.txt` desde un virtualenv limpio sin errores de conflicto. Si hay conflictos con `langgraph-checkpoint-redis` por `langchain-core`, agregar `langchain-core>=1.0.5` explícitamente (dep transitiva aceptada, sin usarla en código propio).

- [ ] **2. Crear `ia-master-brain/llm/bedrock_client.py`** (nuevo paquete `llm/`)
  
  Implementar `BedrockClient` con: `__init__()` crea `boto3.client("bedrock-runtime")`, `invoke_simple(prompt, model=None)` para triage/routing, `invoke_with_tools(messages, system_prompt, tools, model=None)` para llamadas con tools, y `run_agent_loop(messages, system_prompt, tools, tool_executor, max_iterations=10)` que maneja el loop completo `tool_use → call → end_turn`. También crear `llm/__init__.py` vacío.  
  Files: `ia-master-brain/llm/__init__.py`, `ia-master-brain/llm/bedrock_client.py`  
  Verify: `python -c "from llm.bedrock_client import BedrockClient; b = BedrockClient(); print(b.invoke_simple('Decí hola'))"` ejecutado desde `ia-master-brain/` retorna una respuesta de texto sin excepciones.

- [ ] **3. Crear `ia-master-brain/mcp/client.py`** (nuevo paquete `mcp/`)
  
  Implementar: `initialize_mcp(url)` que lanza un daemon thread con event loop propio, establece la sesión `streamablehttp_client` y espera hasta 10s a que `_tools_bedrock_cache` esté disponible. `get_tools_for_bedrock()` retorna la lista de dicts en formato `toolSpec` de Bedrock. `get_tools_by_domain(domain)` filtra la lista por los sets `BILLING_TOOLS`, `RETENTION_TOOLS`, `PROMISE_TOOLS`, `GENERAL_TOOLS`. `call_tool_sync(name, args)` ejecuta la llamada MCP de forma síncrona retornando el resultado como string JSON. `_convert_to_bedrock_format(mcp_tools)` convierte los objetos `Tool` del SDK al formato `{"toolSpec": {...}}` — verificar si `inputSchema` requiere `.model_dump()`. También crear `mcp/__init__.py` vacío.  
  Files: `ia-master-brain/mcp/__init__.py`, `ia-master-brain/mcp/client.py`  
  Verify: Con el back Java corriendo, ejecutar `python -c "from mcp.client import initialize_mcp, get_tools_for_bedrock; initialize_mcp('http://localhost:8080/mcp'); import time; time.sleep(2); tools = get_tools_for_bedrock(); print(len(tools), [t['toolSpec']['name'] for t in tools])"` desde `ia-master-brain/` — debe imprimir 16 nombres de tools en snake_case.

- [ ] **4. Crear `ia-master-brain/graph/state.py`** (nuevo paquete `graph/`)
  
  Implementar `AgentState` TypedDict con los campos: `customer_id: str`, `domain: str`, `paso_actual: str`, `messages: Annotated[list[dict], _merge_messages]`, `cliente: Optional[dict]`, `facturas: Optional[list]`, `servicios: Optional[list]`, `eligibility: Optional[dict]`, `ofertas_preview: Optional[list]`, `ticket_id: Optional[str]`, `ultima_respuesta: str`. Implementar `_merge_messages(existing, new)` como reducer que acumula listas. Sin ningún import de `langchain_core`. También crear `graph/__init__.py` vacío.  
  Files: `ia-master-brain/graph/__init__.py`, `ia-master-brain/graph/state.py`  
  Verify: `python -c "from graph.state import AgentState; print('OK')"` desde `ia-master-brain/` sin excepciones.

- [ ] **5. Modificar `ia-master-brain/context_llm/contexts.py`**
  
  Agregar las constantes de strings: `ROUTING_PROMPT` (clasificador billing/retention/promise/general), `_BILLING_SYSTEM` (fusión de `agent_facturacion` + `EXPLICACION_FACTURA` + reglas de `EXPLICACION_SERVICIOS` + instrucción de promesa de pago al final), `_RETENTION_SYSTEM` (fusión con `PROMPT_NEGOCIACION` completo incluyendo formato de presentación y fórmula de cashback), `_PROMISE_SYSTEM` (fusión con `PROMPT_PROMISE_1` incluyendo el caso `puede_prometer=False`, las 48hs fijas y los datos de ClaroPay), `_GENERAL_SYSTEM` (nuevo, para consultas que no son billing/retention/promise). Implementar `get_system_prompt(domain: str, customer_id: str) -> str` que formatea el template correcto con `{customer_id}`. Mantener las funciones `agent_facturacion()` y `route_prompt()` como aliases para no romper imports todavía.  
  Files: `ia-master-brain/context_llm/contexts.py`  
  Verify: `python -c "from context_llm.contexts import get_system_prompt; print(get_system_prompt('billing', '42')[:100])"` desde `ia-master-brain/` muestra el inicio del prompt de billing con el ID correcto.

- [ ] **6. Modificar `ia-master-brain/memory/memory_brain.py`**
  
  Eliminar todos los imports de `langchain_core.messages` (`AIMessage`, `HumanMessage`, `BaseMessage`). Cambiar `_row_to_message(rol, contenido)` para retornar `{"role": role, "content": [{"text": contenido or ""}]}` donde `role` es `"user"` si `rol` es `"human"/"user"/"humanmessage"`, `"assistant"` en cualquier otro caso. Cambiar el tipo de retorno de `get_full_conversation()` a `list[dict]` (el checkpointer ya guarda los dicts del nuevo `AgentState`, pero también manejar el caso de lista vacía). Cambiar `save_conversation()` para serializar dicts: `rol = m.get("role", "user")` y `contenido = " ".join(b.get("text","") for b in m.get("content",[]) if "text" in b)`. No tocar `get_checkpointer()`, `close_checkpointer()`, `get_memory()`.  
  Files: `ia-master-brain/memory/memory_brain.py`  
  Verify: `python -c "from memory.memory_brain import get_checkpointer; cp = get_checkpointer(); print(type(cp).__name__)"` desde `ia-master-brain/` retorna `"RedisSaver"` o `"MemorySaver"` sin excepciones. El módulo no debe importar nada de `langchain_core`.

- [ ] **7. Crear `ia-master-brain/graph/nodes.py`**
  
  Implementar las constantes de prompts de routing (mover de `context_llm` o importar directamente). Implementar `routing_node(state)`: lee `domain` y `paso_actual` del estado, si hay conversación activa y no está cerrada retorna `{}`, si está cerrada o es primera vez llama `_classify_domain()` con el texto del último mensaje `role="user"`. Implementar `_classify_domain(text)` que llama `BedrockClient().invoke_simple(_ROUTING_PROMPT.format(input_text=text))` y mapea la respuesta a `"billing"|"retention"|"promise"|"general"`. Implementar `billing_agent_node`, `retention_agent_node`, `promise_agent_node`, `general_agent_node` como wrappers de `_run_agent(state, domain)`. Implementar `_run_agent(state, domain)` que: obtiene tools via `get_tools_by_domain(domain)`, obtiene system prompt via `get_system_prompt(domain, customer_id)`, llama `_bedrock.run_agent_loop(messages, system, tools, call_tool_sync)`, retorna `{"messages": new_messages, "ultima_respuesta": final_text, "paso_actual": _detect_paso(final_text, domain)}`. Implementar `_detect_paso(text, domain)` con marcadores por dominio: `"retencion_aplicada"` si el texto menciona "Retención registrada" o "promociones quedaron registradas"; `"promesa_procesada"` si menciona "promesa de pago quedó registrada"; `"reclamo_procesado"` si menciona "Reclamo registrado"; `"respondido"` si menciona "¿Necesitás algo más?" o "¿hay algo más"; `"en_progreso"` en cualquier otro caso.  
  Files: `ia-master-brain/graph/nodes.py`  
  Verify: `python -c "from graph.nodes import routing_node, billing_agent_node; print('OK')"` desde `ia-master-brain/` sin excepciones. (La verificación funcional completa ocurre en el paso 9.)

- [ ] **8. Crear `ia-master-brain/graph/agent_graph.py`**
  
  Implementar `build_agent_graph(checkpointer)` que: crea un `StateGraph(AgentState)`, registra los 5 nodos (`routing`, `billing_agent`, `retention_agent`, `promise_agent`, `general_agent`), define el entry point en `routing`, agrega `add_conditional_edges` desde `routing` usando `lambda state: state.get("domain", "general")` con el mapeo a los 4 nodos agente, agrega `add_edge` de cada nodo agente a `END`, compila con `checkpointer=checkpointer` y retorna el grafo compilado.  
  Files: `ia-master-brain/graph/agent_graph.py`  
  Verify: `python -c "from memory.memory_brain import get_checkpointer; from graph.agent_graph import build_agent_graph; g = build_agent_graph(get_checkpointer()); print(type(g).__name__)"` desde `ia-master-brain/` retorna `"CompiledStateGraph"` sin excepciones.

- [ ] **9. Modificar `ia-master-brain/kafka/consumer.py`**
  
  Eliminar: `from langchain_core.messages import HumanMessage`, `from connection_llm.llm_conecction import get_bedrock_model_master as llm_master`, `from agents.llm_brain import LlmBrain`, `chat_haiku = llm_master()`, la instanciación de `LlmBrain`, y `_last_ai_content()` (ya no es necesaria). Eliminar la función `triage()` basada en LangChain y `responder_consulta()` basada en LangChain. Agregar: `from mcp.client import initialize_mcp`, `from llm.bedrock_client import BedrockClient`, `from graph.agent_graph import build_agent_graph`, `from memory.memory_brain import get_checkpointer, close_checkpointer, get_memory, save_conversation`. Al inicio del módulo, llamar `initialize_mcp(os.getenv("MCP_URL", "http://localhost:8080/mcp"))`. Instanciar `bedrock = BedrockClient()` y `brain_graph = build_agent_graph(get_checkpointer())`. Reimplementar `triage()` usando `bedrock.invoke_simple(prompt)`. Reimplementar `responder_consulta()` usando `bedrock.invoke_simple(prompt)`. Reimplementar `procesar_brain()` para construir `user_message = {"role": "user", "content": [{"text": content}]}`, llamar `brain_graph.invoke(...)`, extraer `ultima_respuesta` del resultado, y detectar cierre con el conjunto `PASOS_CERRADOS`. Mantener: la lógica de Redis db=1 para ruteo, la lógica de routing BRAIN/CONSULTA/triage, `send_response()`, `_clean_response()`, el loop Kafka, `close_checkpointer()` en el finally.  
  Files: `ia-master-brain/kafka/consumer.py`  
  Verify: `python -m kafka.consumer` desde `ia-master-brain/` arranca sin excepciones, muestra logs `[MCP] Tools cargadas: 16` y `[AgentGraph compilado]`. Enviar un mensaje de prueba al topic `consultas.usuario` y verificar que aparece respuesta en `respuestas.agente`.

- [ ] **10. Actualizar `.env.example` y `docker-compose.yml`**
  
  `.env.example`: agregar línea `MCP_URL=http://localhost:8080/mcp`.  
  `docker-compose.yml`: agregar `MCP_URL: http://cliente-back:8080/mcp` en `ia_brain.environment`. Los env vars `BACK_API`, `BACK_RETENTION_API`, `BACK_SERVICE_API` se pueden dejar por ahora (son inofensivos), pero se eliminarán cuando se borren los módulos `tools/`.  
  Files: `/home/facuvega/Documents/agent-internet-ia/.env.example`, `/home/facuvega/Documents/agent-internet-ia/docker-compose.yml`  
  Verify: `docker compose config` no muestra errores de sintaxis.

- [ ] **11. Test de integración end-to-end con `docker compose up`**
  
  Asegurarse de que el back Java ya tiene Spring AI MCP configurado (prerequisito externo). Ejecutar `docker exec redis_memory redis-cli -n 0 FLUSHDB` para limpiar checkpoints antiguos. Levantar `docker compose up --build ia_brain`. Verificar en los logs que los 16 tools MCP se cargan. Enviar un mensaje de test a través del frontend o directamente al topic Kafka. Verificar que la respuesta llega al topic `respuestas.agente` con la estructura esperada.  
  Files: ninguno  
  Verify: `docker compose logs ia_brain | grep "MCP"` muestra `"MCP client inicializado"` y `"Tools cargadas: 16"` sin errores. Un mensaje de facturación retorna una respuesta coherente.

- [ ] **12. Eliminar módulos obsoletos** (solo después de verificar el paso 11)
  
  Eliminar: `ia-master-brain/agents/` (directorio completo), `ia-master-brain/tools/` (directorio completo), `ia-master-brain/flows/` (directorio completo), `ia-master-brain/connection_llm/` (directorio completo). Eliminar de `docker-compose.yml` las variables `BACK_API`, `BACK_RETENTION_API`, `BACK_SERVICE_API` del servicio `ia_brain`.  
  Files: los directorios mencionados, `docker-compose.yml`  
  Verify: `docker compose up --build ia_brain` sigue funcionando correctamente después de la eliminación. `python -m kafka.consumer` desde `ia-master-brain/` no tiene imports rotos.

---

## 7. Prerequisito externo: Spring AI MCP en el back Java

Los pasos 3 y 9+ requieren que el back Java esté sirviendo los 16 tools via MCP. El detalle completo de la implementación Java está en `mcp-migration-design.md` secciones 2.1–2.8. Resumen de los cambios necesarios:

1. Agregar BOM de Spring AI 2.0.0 en `pom.xml` y la dependencia `spring-ai-starter-mcp-server-webmvc`
2. Configurar `spring.ai.mcp.server.protocol=STREAMABLE` en `application.properties`
3. Crear clases `@Service` anotadas con `@Tool` en el paquete `com.cliente.cliente_back.mcp/`:
   - `CustomerMcpTools.java` (get_customer_info, get_customer_service)
   - `BillingMcpTools.java` (billing_info, billing_lookup, payment_promises, create_ticket)
   - `NetworkMcpTools.java` (run_network_diagnostic, list_network_diagnostics, get_latest_network_diagnostic, request_connection_reset, grant_mobile_topup)
   - `RetentionMcpTools.java` (get_retention_tiers, get_retention_eligibility, get_retention_preview, apply_retention_agreement)
   - `AvailableServicesMcpTools.java` (list_available_offerings)
4. Cada `@Tool` debe tener `name = "snake_case_name"` explícito para que los nombres coincidan con los filtros de Python

**Si el back Java no está listo:** Para desarrollo local de los pasos 2–8, se puede usar un servidor MCP mock con `FastMCP` en Python que sirva los 16 tools con datos hardcodeados. Esto permite desarrollar y verificar la capa Python de forma independiente.

---

## 8. Notas de riesgo para el implementador

1. **Loop async en consumer síncrono:** La implementación de `call_tool_sync` que abre un nuevo event loop por llamada (`asyncio.new_event_loop()`) es la más simple pero puede tener edge cases con threads. Si hay problemas de "event loop is closed" o "Future is attached to a different loop", la alternativa es usar `asyncio.run_coroutine_threadsafe(coro, _mcp_loop)` donde `_mcp_loop` es el loop del thread daemon de MCP.

2. **Redis db=0 FLUSHDB en cutover:** Los checkpoints previos con `BaseMessage` no son compatibles con el nuevo formato `dict`. El FLUSHDB debe hacerse en la ventana de mantenimiento, acepta perder el estado de conversaciones activas en ese momento.

3. **Pinear versión de `mcp` SDK:** La API de `streamablehttp_client` puede cambiar entre versiones 1.x. El diseño recomienda `mcp==1.9.4` (verificar versión más reciente estable en PyPI antes de implementar).

4. **`inputSchema` como Pydantic vs dict:** Si `tool.inputSchema` en el MCP Python SDK retorna un objeto Pydantic, `_convert_to_bedrock_format()` necesita `tool.inputSchema.model_dump()` en lugar de pasar el objeto directamente.

5. **`customer_id` int vs str:** Los tools MCP Java esperan `integer`. Bedrock puede enviarlos como string desde el LLM. Verificar que Spring AI hace el casting antes de asumir que funciona.
