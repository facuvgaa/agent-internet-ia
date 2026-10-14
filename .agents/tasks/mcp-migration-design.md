# Diseño Técnico: Migración a MCP (cliente_back → MCP Server, ia-master-brain → MCP Client)

## Resumen ejecutivo

El proyecto está en muy buen estado para esta migración. El back Java (`cliente_back`) corre sobre **Spring Boot 4.0.3** — versión que no soporta Spring AI 1.x (que requiere Spring Boot 3.x). La versión compatible es **Spring AI 2.0.0 GA** (lanzada junio 2026), que sí soporta Spring Boot 4.x y tiene soporte nativo para MCP Server via `spring-ai-starter-mcp-server-webmvc` con protocolo **Streamable-HTTP** (SSE está deprecado en 2.0). En Python, la librería a usar es **`langchain-mcp-adapters==0.3.2`**, compatible con las versiones actuales del proyecto (`langchain>=0.3.0`, `langgraph>=0.2.0`). Los grafos LangGraph **no necesitan refactor estructural** — solo cambia la fuente de las tools (de `@tool` HTTP a tools MCP), lo cual requiere inicialización async en el arranque del consumer.

---

## 1. Inventario de tools MCP

Cada operación del back Java se convierte en un tool MCP. A continuación el contrato completo para cada uno.

### 1.1 Herramientas del dominio `internet-ia`

#### `get_customer_info`
- **Descripción para el LLM**: Retorna datos del cliente (nombre, email, teléfono, estado de cuenta). Usar antes de cualquier gestión para confirmar identidad y estado.
- **Endpoint REST**: `GET /api/v1/internet-ia/customers/{customerId}`
- **Input Schema**:
  ```json
  {
    "type": "object",
    "properties": {
      "customer_id": { "type": "integer", "description": "ID numérico del cliente" }
    },
    "required": ["customer_id"]
  }
  ```
- **Ejemplo**: `{"customer_id": 42}` → `{"id": 42, "name": "Ana García", "email": "ana@mail.com", "status": "ACTIVE"}`

---

#### `get_customer_service`
- **Descripción para el LLM**: Lista los servicios/líneas contratadas del cliente (nombre del plan, dirección, precio mensual, descuento vigente, id de servicio). Necesario para operaciones sobre líneas específicas.
- **Endpoint REST**: `GET /api/v1/internet-ia/customers/services/{customerId}`
- **Input Schema**:
  ```json
  {
    "type": "object",
    "properties": {
      "customer_id": { "type": "integer", "description": "ID numérico del cliente" }
    },
    "required": ["customer_id"]
  }
  ```

---

#### `create_ticket`
- **Descripción para el LLM**: Abre un ticket de reclamo técnico o de facturación. Devuelve el id y estado del ticket creado.
- **Endpoint REST**: `POST /api/v1/internet-ia/tickets`
- **Input Schema**:
  ```json
  {
    "type": "object",
    "properties": {
      "customer_id": { "type": "integer" },
      "subject":     { "type": "string", "description": "Descripción del reclamo (máx 200 chars)" },
      "priority":    { "type": "string", "enum": ["alta", "media", "baja"] }
    },
    "required": ["customer_id", "subject", "priority"]
  }
  ```

---

#### `billing_info`
- **Descripción para el LLM**: Retorna todas las facturas del cliente (estado, monto, fecha vencimiento, número de factura). Usar para consultas de cuenta corriente o identificar facturas impagas.
- **Endpoint REST**: `GET /api/v1/internet-ia/billing/customer/{customerId}`
- **Input Schema**:
  ```json
  {
    "type": "object",
    "properties": {
      "customer_id": { "type": "integer" }
    },
    "required": ["customer_id"]
  }
  ```

---

#### `billing_lookup`
- **Descripción para el LLM**: Busca una factura específica del cliente por número de factura impreso o id. Útil cuando el cliente menciona un número de comprobante o una foto de factura.
- **Endpoint REST**: `GET /api/v1/internet-ia/billing/customer/{customerId}/lookup?invoiceNumber={n}`
- **Input Schema**:
  ```json
  {
    "type": "object",
    "properties": {
      "customer_id":     { "type": "integer" },
      "invoice_number":  { "type": "string", "description": "Número de factura o período" }
    },
    "required": ["customer_id", "invoice_number"]
  }
  ```

---

#### `payment_promises`
- **Descripción para el LLM**: Registra un compromiso de pago sobre una factura impaga. Permite al cliente comprometerse a pagar hasta una fecha futura sin corte de servicio.
- **Endpoint REST**: `POST /api/v1/internet-ia/payment-promises`
- **Input Schema**:
  ```json
  {
    "type": "object",
    "properties": {
      "customer_id":    { "type": "integer" },
      "billing_id":     { "type": "integer", "description": "ID de la factura (de billing_info)" },
      "promise_until":  { "type": "string", "description": "Fecha ISO-8601 futura. Ej: 2025-09-30T23:59:59" }
    },
    "required": ["customer_id", "billing_id", "promise_until"]
  }
  ```

---

#### `grant_mobile_topup`
- **Descripción para el LLM**: Otorga compensación de 10 GB móviles al cliente como resarcimiento por una falla técnica registrada en un ticket. Requiere el número de teléfono normalizado.
- **Endpoint REST**: `POST /api/v1/internet-ia/mobile-topups`
- **Input Schema**:
  ```json
  {
    "type": "object",
    "properties": {
      "customer_id":  { "type": "integer" },
      "ticket_id":    { "type": "integer" },
      "msisdn":       { "type": "string", "description": "Número de teléfono normalizado" },
      "reason":       { "type": "string", "description": "Motivo (opcional)" }
    },
    "required": ["customer_id", "ticket_id", "msisdn"]
  }
  ```

---

#### `request_connection_reset`
- **Descripción para el LLM**: Encola un reset de conexión (reinicio CPE/señal) para una línea específica del cliente. Usar cuando hay problemas de conectividad reportados.
- **Endpoint REST**: `POST /api/v1/internet-ia/connection-resets`
- **Input Schema**:
  ```json
  {
    "type": "object",
    "properties": {
      "customer_id":  { "type": "integer" },
      "service_id":   { "type": "integer", "description": "ID de la línea a resetear" },
      "reason":       { "type": "string" }
    },
    "required": ["customer_id", "service_id", "reason"]
  }
  ```

---

#### `run_network_diagnostic`
- **Descripción para el LLM**: Ejecuta un diagnóstico de red en tiempo real para la línea indicada. Devuelve estado de señal, latencia y problemas detectados.
- **Endpoint REST**: `POST /api/v1/internet-ia/network-diagnostics`
- **Input Schema**:
  ```json
  {
    "type": "object",
    "properties": {
      "customer_id":  { "type": "integer" },
      "service_id":   { "type": "integer" },
      "channel":      { "type": "string", "default": "IA" }
    },
    "required": ["customer_id", "service_id"]
  }
  ```

---

#### `list_network_diagnostics`
- **Descripción para el LLM**: Lista el historial de diagnósticos de red para una línea, ordenado del más reciente al más antiguo.
- **Endpoint REST**: `GET /api/v1/internet-ia/network-diagnostics/customers/{customerId}/services/{serviceId}`
- **Input Schema**:
  ```json
  {
    "type": "object",
    "properties": {
      "customer_id":  { "type": "integer" },
      "service_id":   { "type": "integer" }
    },
    "required": ["customer_id", "service_id"]
  }
  ```

---

#### `get_latest_network_diagnostic`
- **Descripción para el LLM**: Retorna el diagnóstico de red más reciente para una línea. Más eficiente que `list_network_diagnostics` cuando solo importa el último estado.
- **Endpoint REST**: `GET /api/v1/internet-ia/network-diagnostics/customers/{customerId}/services/{serviceId}/latest`
- **Input Schema**: igual a `list_network_diagnostics`

---

### 1.2 Herramientas del dominio `retention`

#### `get_retention_tiers`
- **Descripción para el LLM**: Retorna el catálogo de niveles de retención disponibles (niveles 1–4 con porcentaje de descuento y duración en meses).
- **Endpoint REST**: `GET /api/v1/retention/tiers`
- **Input Schema**: `{ "type": "object", "properties": {} }`

---

#### `get_retention_eligibility`
- **Descripción para el LLM**: Verifica si el cliente puede acceder a promociones de retención y qué niveles le aplican. Siempre llamar antes de generar una oferta.
- **Endpoint REST**: `GET /api/v1/retention/customers/{customerId}/eligibility?serviceId={n}`
- **Input Schema**:
  ```json
  {
    "type": "object",
    "properties": {
      "customer_id":  { "type": "integer" },
      "service_id":   { "type": "integer", "description": "Opcional: ID de línea específica" }
    },
    "required": ["customer_id"]
  }
  ```

---

#### `get_retention_preview`
- **Descripción para el LLM**: Simula el precio con descuento de retención para un nivel dado (1–4). No guarda nada — solo para mostrarle la oferta al cliente antes de que acepte.
- **Endpoint REST**: `POST /api/v1/retention/preview`
- **Input Schema**:
  ```json
  {
    "type": "object",
    "properties": {
      "customer_id":  { "type": "integer" },
      "service_id":   { "type": "integer" },
      "level":        { "type": "integer", "minimum": 1, "maximum": 4 }
    },
    "required": ["customer_id", "service_id", "level"]
  }
  ```

---

#### `apply_retention_agreement`
- **Descripción para el LLM**: Registra definitivamente el acuerdo de retención después de que el cliente aceptó la oferta. Persistente — genera descuento real en la cuenta.
- **Endpoint REST**: `POST /api/v1/retention/applications`
- **Input Schema**:
  ```json
  {
    "type": "object",
    "properties": {
      "customer_id":       { "type": "integer" },
      "service_id":        { "type": "integer" },
      "level":             { "type": "integer", "minimum": 1, "maximum": 4 },
      "idempotency_key":   { "type": "string", "description": "Clave de idempotencia opcional" },
      "channel":           { "type": "string", "default": "IA" },
      "notes":             { "type": "string" }
    },
    "required": ["customer_id", "service_id", "level"]
  }
  ```

---

### 1.3 Herramientas del dominio `available-services`

#### `list_available_offerings`
- **Descripción para el LLM**: Lista los add-ons y servicios adicionales disponibles para contratar según el perfil del cliente (cupos disponibles por tipo de oferta).
- **Endpoint REST**: `GET /api/v1/available-services/customers/{customerId}/offerings`
- **Input Schema**:
  ```json
  {
    "type": "object",
    "properties": {
      "customer_id": { "type": "integer" }
    },
    "required": ["customer_id"]
  }
  ```

---

## 2. Diseño del MCP Server en Spring Boot

### 2.1 Problema crítico de versiones

El `pom.xml` actual usa **Spring Boot 4.0.3**. Spring AI 1.x soporta solo Spring Boot 3.x. Para Spring Boot 4.x la versión compatible es **Spring AI 2.0.0** (GA desde junio 2026), que introduce `@McpTool` annotations y requiere Spring Framework 7.

**Decisión**: usar **Spring AI 2.0.0 + `spring-ai-starter-mcp-server-webmvc`** con transporte **`STREAMABLE`** (SSE está deprecado en 2.0). El protocolo Streamable-HTTP es compatible con `langchain-mcp-adapters` 0.3 via tipo de conexión `streamable_http`.

> Fuentes: [Spring AI 2.0.0 GA](https://spring.io/blog/2026/06/12/spring-ai-2-0-0-GA-available-now), [MCP Server Boot Starter docs](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-server-boot-starter-docs.html), [Spring Boot 4 + Spring AI 2.0 migration](https://github.com/embabel/embabel-agent/wiki/Spring-Boot-4---Spring-AI-2.0-Migration)

### 2.2 Cambios en `pom.xml`

Agregar el BOM de Spring AI y el starter MCP:

```xml
<!-- En <dependencyManagement> -->
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.springframework.ai</groupId>
      <artifactId>spring-ai-bom</artifactId>
      <version>2.0.0</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<!-- En <dependencies> -->
<dependency>
  <groupId>org.springframework.ai</groupId>
  <artifactId>spring-ai-starter-mcp-server-webmvc</artifactId>
</dependency>
```

**Nota**: No agregar `spring-boot-starter-webflux`. El proyecto ya usa `spring-boot-starter-webmvc`. El starter `spring-ai-starter-mcp-server-webmvc` es la opción correcta para proyectos MVC síncronos.

### 2.3 Configuración en `application.properties`

```properties
# MCP Server
spring.ai.mcp.server.name=cliente-back-mcp
spring.ai.mcp.server.version=1.0.0
spring.ai.mcp.server.protocol=STREAMABLE
spring.ai.mcp.server.instructions=Servidor MCP para operaciones de telecomunicaciones: clientes, facturas, retención, diagnóstico de red.
```

El endpoint Streamable-HTTP quedará expuesto en: `http://localhost:8080/mcp/message` (POST) con soporte SSE opcional en respuestas multi-evento.

### 2.4 Estructura de clases propuesta

Crear un nuevo paquete `com.cliente.cliente_back.mcp`:

```
src/main/java/com/cliente/cliente_back/mcp/
├── CustomerMcpTools.java         ← tools de clientes e internet-ia
├── BillingMcpTools.java          ← tools de facturación
├── RetentionMcpTools.java        ← tools de retención
├── NetworkMcpTools.java          ← tools de diagnóstico de red
└── AvailableServicesMcpTools.java ← tools de add-ons
```

### 2.5 Ejemplo de implementación: `CustomerMcpTools.java`

```java
package com.cliente.cliente_back.mcp;

import com.cliente.cliente_back.dto.CustomerDTO;
import com.cliente.cliente_back.dto.ServicesDTO;
import com.cliente.cliente_back.services.CustomerService;
import com.cliente.cliente_back.services.ServicesService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CustomerMcpTools {

    private final CustomerService customerService;
    private final ServicesService servicesService;

    public CustomerMcpTools(CustomerService customerService, ServicesService servicesService) {
        this.customerService = customerService;
        this.servicesService = servicesService;
    }

    @Tool(description = "Retorna datos del cliente (nombre, email, teléfono, estado). Usar antes de cualquier gestión.")
    public CustomerDTO getCustomerInfo(
            @ToolParam(description = "ID numérico del cliente") Long customerId) {
        return customerService.getCustomerById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Cliente no encontrado: " + customerId));
    }

    @Tool(description = "Lista los servicios/líneas contratadas del cliente (planes, dirección, precios).")
    public List<ServicesDTO> getCustomerService(
            @ToolParam(description = "ID numérico del cliente") Long customerId) {
        return servicesService.findAllByCustomerId(customerId);
    }
}
```

### 2.6 Ejemplo: `BillingMcpTools.java`

```java
@Service
public class BillingMcpTools {

    private final BillingService billingService;
    private final PaymentPromiseService paymentPromiseService;
    private final TicketRequestService ticketRequestService;
    // ... otros services

    @Tool(description = "Retorna todas las facturas del cliente con estado e importes.")
    public List<BillingDTO> billingInfo(
            @ToolParam(description = "ID del cliente") Long customerId) {
        return billingService.findAllBilling(customerId);
    }

    @Tool(description = "Busca una factura por número impreso o ID. Útil con comprobantes del cliente.")
    public BillingDTO billingLookup(
            @ToolParam(description = "ID del cliente") Long customerId,
            @ToolParam(description = "Número de factura o período") String invoiceNumber) {
        return billingService
                .findBillingByCustomerAndInvoiceNumber(customerId, invoiceNumber)
                .orElseThrow(() -> new IllegalArgumentException("Factura no encontrada"));
    }

    @Tool(description = "Registra compromiso de pago sobre una factura impaga. Evita corte de servicio.")
    public PaymentPromiseDTO paymentPromises(
            @ToolParam(description = "ID del cliente") Long customerId,
            @ToolParam(description = "ID de la factura") Long billingId,
            @ToolParam(description = "Fecha ISO-8601 límite de pago") String promiseUntil) {
        var request = new PaymentPromiseDTO();
        request.setCustomerId(customerId);
        request.setBillingId(billingId);
        request.setPromiseUntil(promiseUntil);
        return paymentPromiseService.createPromise(request);
    }
}
```

### 2.7 Patrón para los demás `McpTools`

Cada clase `McpTools` sigue el mismo patrón:
- Anotada con `@Service` (Spring AI la escanea automáticamente)
- Inyecta el/los services existentes vía constructor (no cambia la lógica de negocio)
- Cada método público anotado con `@Tool(description = "...")` y parámetros con `@ToolParam`
- Devuelve directamente el DTO — Spring AI serializa a JSON automáticamente
- Nunca devolver `null` — lanzar excepción o retornar objeto vacío

**Las clases existentes (services, repositories, entities, mappers) no se modifican.**

### 2.8 Endpoint SSE/Streamable expuesto

Con `STREAMABLE` configurado, Spring AI expone automáticamente:
- `POST /mcp/message` — endpoint principal JSON-RPC 2.0
- El endpoint acepta requests MCP (tools/call, tools/list, etc.) y puede responder con SSE streaming

---

## 3. Diseño del cliente MCP en ia-master-brain

### 3.1 Librería a usar

**`langchain-mcp-adapters==0.3.2`** (última versión estable a julio 2025).

- Compatible con `langchain>=0.3.0` y `langgraph>=0.2.0` (versiones actuales del proyecto)
- Proporciona `MultiServerMCPClient` que carga tools MCP como `BaseTool` de LangChain
- Soporta transporte `streamable_http` (compatible con el server Spring AI 2.0)
- Python 3.10+ requerido (el proyecto ya usa Python 3.12 — confirmado en el Dockerfile)

> Fuente: [langchain-mcp-adapters PyPI](https://pypi.org/project/langchain-mcp-adapters/0.3.2), [deepwiki.com análisis del paquete](https://deepwiki.com/langchain-ai/langchain-mcp-adapters/1.1-installation)

### 3.2 Cambios en `requirements.txt`

```text
langchain>=0.3.0
langchain-core>=0.3.0
langchain-aws>=0.2.0
langgraph>=0.2.0
langgraph-checkpoint-postgres>=3.0.4
langgraph-checkpoint-redis>=0.4.0
boto3>=1.34.0
confluent-kafka>=2.3.0
python-dotenv>=1.0.1
pydantic>=2.0,<3
# NUEVO: cliente MCP
langchain-mcp-adapters==0.3.2
mcp>=1.0.0
```

### 3.3 Nuevo archivo `tools/mcp_client.py`

Este archivo reemplaza la inicialización síncrona de tools en `tools/tools.py`. El cliente MCP es async por naturaleza; hay que inicializarlo una vez en el arranque del consumer.

```python
# tools/mcp_client.py
import asyncio
import os
import logging
from langchain_mcp_adapters.client import MultiServerMCPClient

logger = logging.getLogger(__name__)

_MCP_SERVER_URL = os.getenv("MCP_SERVER_URL", "http://localhost:8080/mcp/message")

_mcp_client: MultiServerMCPClient | None = None
_mcp_tools: list = []


async def _init_mcp() -> list:
    """Inicializa el cliente MCP y carga todos los tools. Llamar una sola vez al arranque."""
    global _mcp_client, _mcp_tools
    
    _mcp_client = MultiServerMCPClient({
        "cliente-back": {
            "url": _MCP_SERVER_URL,
            "transport": "streamable_http",
        }
    })
    
    _mcp_tools = await _mcp_client.get_tools()
    logger.info("[MCP] Tools cargadas: %s", [t.name for t in _mcp_tools])
    return _mcp_tools


def get_mcp_tools() -> list:
    """Retorna las tools ya inicializadas. Requiere haber llamado init_mcp_tools() primero."""
    if not _mcp_tools:
        raise RuntimeError("MCP tools no inicializadas. Llamar init_mcp_tools() primero.")
    return _mcp_tools


def init_mcp_tools() -> list:
    """Wrapper síncrono para inicializar desde código no-async (ej: consumer.py)."""
    return asyncio.run(_init_mcp())
```

### 3.4 Cambios en `tools/__init__.py`

El `__init__.py` actual exporta `ALL_BRAIN_TOOLS`. Necesita adaptarse para soportar ambos modos (legacy + MCP) durante la transición, o directamente reemplazarse:

```python
# tools/__init__.py  (versión nueva)
import os

_USE_MCP = os.getenv("USE_MCP", "true").lower() == "true"

if _USE_MCP:
    from tools.mcp_client import get_mcp_tools
    # Las tools se cargan dinámicamente al arranque del consumer
    ALL_BRAIN_TOOLS = []  # Placeholder — se llena en consumer.py
else:
    # Fallback legacy (útil para desarrollo local sin el back Java)
    from tools.tools import (
        get_customer_info, get_customer_service, create_ticket,
        billing_info, billing_lookup, payment_promises, grant_mobile_topup,
        request_connection_reset, run_network_diagnostic,
        list_network_diagnostics, get_latest_network_diagnostic,
        get_retention_tiers, get_retention_eligibility,
        get_retention_preview, apply_retention_agreement,
        list_available_offerings,
    )
    ALL_BRAIN_TOOLS = [
        get_customer_info, get_customer_service, create_ticket,
        billing_info, billing_lookup, payment_promises, grant_mobile_topup,
        request_connection_reset, run_network_diagnostic,
        list_network_diagnostics, get_latest_network_diagnostic,
        get_retention_tiers, get_retention_eligibility,
        get_retention_preview, apply_retention_agreement,
        list_available_offerings,
    ]
```

### 3.5 Cambios en `kafka/consumer.py`

El consumer es el punto de entrada. Hay que inicializar MCP tools antes de crear el `LlmBrain`:

```python
# Al inicio de consumer.py, antes de instanciar LlmBrain:
import os

_USE_MCP = os.getenv("USE_MCP", "true").lower() == "true"

if _USE_MCP:
    from tools.mcp_client import init_mcp_tools
    import tools as tools_module
    logger.info("[MCP] Inicializando tools MCP...")
    mcp_tools = init_mcp_tools()
    tools_module.ALL_BRAIN_TOOLS = mcp_tools
    logger.info("[MCP] %d tools cargadas desde MCP server", len(mcp_tools))

# Luego, instanciar el brain normalmente:
brain = LlmBrain()
brain.brain()
```

---

## 4. Cambios en LangGraph

### 4.1 Qué NO cambia

Los grafos (`flows/billings/graph.py`, `flows/retention/graph.py`, `flows/promise/graph.py`) **no necesitan modificaciones estructurales**. La topología de nodos, routers y estados es independiente de cómo se llaman los tools.

### 4.2 Qué sí cambia

**En `agents/llm_brain.py`**: el constructor de `LlmBrain` toma `ALL_BRAIN_TOOLS` al momento de instanciarse. Si las tools MCP se cargan antes en `consumer.py` (como se describió en 3.5), el brain las recibe correctamente.

```python
# agents/llm_brain.py  — solo cambia la importación
from tools import ALL_BRAIN_TOOLS  # sigue igual

class LlmBrain:
    def __init__(self) -> None:
        self.tools = ALL_BRAIN_TOOLS  # ya contiene las tools MCP
        self._llm_base = llm_brain()
        self.model = self._llm_base.bind_tools(self.tools)
        # ... resto igual
```

**En los nodos de los flows** (`flows/billings/nodes.py`, `flows/promise/nodes.py`, `flows/retention/nodes.py`): actualmente importan tools individuales directamente:

```python
# billings/nodes.py (actual)
from tools import get_customer_info, billing_info, create_ticket, ...
```

Esto es un problema porque los tools MCP son objetos `BaseTool` cargados dinámicamente, no funciones con nombre fijo. La solución es cambiar el patrón de acceso en los nodos para buscar tools por nombre:

```python
# utils/tool_registry.py  (nuevo archivo)
from tools import ALL_BRAIN_TOOLS

def get_tool(name: str):
    """Retorna el tool por nombre, compatible con tools legacy y MCP."""
    for t in ALL_BRAIN_TOOLS:
        if t.name == name:
            return t
    raise ValueError(f"Tool '{name}' no encontrado en ALL_BRAIN_TOOLS")
```

```python
# billings/nodes.py (modificado)
from utils.tool_registry import get_tool

def nodo_cargar_datos(state: BillingEstate) -> dict:
    customer_id = state["customer_id"]
    get_customer_info = get_tool("get_customer_info")
    billing_info_tool = get_tool("billing_info")
    
    info_cliente = get_customer_info.invoke({"customer_id": customer_id})
    # ... resto igual
```

**Alternativa más limpia**: inyectar los tools en los nodos a través de los parámetros del grafo, como ya hacen los `model` que se pasan a los constructores de grafos. Esto implica pasar un `tools_registry` dict al `build_*_graph()` — más verboso pero más testeable.

### 4.3 Consideración sobre nombres de tools MCP

Los tools MCP cargan con el nombre que Spring AI les asigna según el nombre del método Java (camelCase convertido a snake_case o según la `description`). Es crítico que los nombres coincidan con lo que los nodos buscan. El servidor Spring AI 2.0 usa el nombre del método Java como `name` del tool por defecto (ej: método `getCustomerInfo` → tool name `getCustomerInfo`). 

**Recomendación**: en cada `@Tool` del Java, agregar el atributo `name` explícitamente:
```java
@Tool(name = "get_customer_info", description = "...")
public CustomerDTO getCustomerInfo(...)
```

---

## 5. Cambios de infraestructura

### 5.1 Puerto SSE/MCP del server

El `cliente_back` ya corre en el puerto `8080`. El endpoint MCP se expone en ese mismo puerto en la ruta `/mcp/message` — no se necesita un puerto adicional.

### 5.2 Cambios en `docker-compose.yml`

Solo se agrega una variable de entorno al servicio `ia_brain`:

```yaml
ia_brain:
  # ... todo lo existente se mantiene igual
  environment:
    # Agregar:
    MCP_SERVER_URL: http://cliente-back:8080/mcp/message
    USE_MCP: "true"
    # ... resto de variables sin cambios
```

El servicio `cliente_back` no necesita cambios en el compose — mismo puerto, mismo nombre de container.

### 5.3 Variables de entorno nuevas

| Variable | Valor en Docker | Valor local dev |
|----------|----------------|----------------|
| `MCP_SERVER_URL` | `http://cliente-back:8080/mcp/message` | `http://localhost:8080/mcp/message` |
| `USE_MCP` | `true` | `false` (para dev local sin back Java) |

---

## 6. Plan de implementación paso a paso

### Fase 1: MCP Server en Java (sin tocar Python)

**Paso 1 — Actualizar `pom.xml`**
- Agregar BOM de Spring AI 2.0.0 en `<dependencyManagement>`
- Agregar dependencia `spring-ai-starter-mcp-server-webmvc`
- Verificar: `./mvnw compile` debe compilar sin errores

**Paso 2 — Configurar `application.properties`**
- Agregar las 4 líneas de configuración MCP (`spring.ai.mcp.server.*`)
- No cambiar ninguna configuración existente (datasource, kafka, websocket)

**Paso 3 — Crear clases `*McpTools.java`** en `com.cliente.cliente_back.mcp`
- Crear `CustomerMcpTools`, `BillingMcpTools`, `RetentionMcpTools`, `NetworkMcpTools`, `AvailableServicesMcpTools`
- Cada una inyecta los services existentes y expone cada operación como `@Tool`
- **No tocar** los services, repositories ni DTOs existentes
- Verificar: `./mvnw spring-boot:run` → acceder a `http://localhost:8080/mcp/message` con un request `tools/list` debe devolver los 16 tools

**Paso 4 — Probar el server MCP con cliente Python manual**
- Desde una terminal Python: `MultiServerMCPClient` → `get_tools()` → verificar que devuelve los 16 tools con los nombres esperados
- Ajustar `@Tool(name = "...")` en Java si los nombres no coinciden con los esperados por los nodos Python

### Fase 2: Cliente MCP en Python (con feature flag)

**Paso 5 — Agregar dependencias Python**
- Editar `requirements.txt` en la raíz: agregar `langchain-mcp-adapters==0.3.2` y `mcp>=1.0.0`
- Verificar: `pip install -r requirements.txt` sin conflictos

**Paso 6 — Crear `tools/mcp_client.py`**
- Implementar `init_mcp_tools()` y `get_mcp_tools()` como se describió en 3.3
- Verificar: llamar `init_mcp_tools()` apuntando al back Java debe retornar lista de tools

**Paso 7 — Actualizar `tools/__init__.py`**
- Agregar lógica de feature flag `USE_MCP`
- El modo legacy (`USE_MCP=false`) debe seguir funcionando sin cambios

**Paso 8 — Crear `utils/tool_registry.py`**
- Implementar `get_tool(name)` para lookup por nombre

**Paso 9 — Modificar los nodos de los flows**
- `flows/billings/nodes.py` — reemplazar imports directos por `get_tool()`
- `flows/promise/nodes.py` — ídem
- `flows/retention/nodes.py` — ídem
- Verificar: con `USE_MCP=false` los tests o invocaciones manuales funcionan igual

**Paso 10 — Modificar `kafka/consumer.py`**
- Agregar bloque de inicialización MCP antes de instanciar `LlmBrain`
- Verificar con `USE_MCP=true`: el consumer arranca, los tools se cargan, un mensaje de test produce respuesta correcta via MCP

### Fase 3: Integración y docker-compose

**Paso 11 — Actualizar `docker-compose.yml`**
- Agregar `MCP_SERVER_URL` y `USE_MCP` al servicio `ia_brain`
- Verificar: `docker compose up --build` levanta toda la stack, el brain se conecta al MCP server

---

## 7. Qué NO cambia

| Componente | Estado |
|-----------|--------|
| Kafka (consumer/producer) | Sin cambios — sigue siendo el canal de entrada/salida |
| WebSocket (`ChatController`, `WebSocketConfig`) | Sin cambios — sigue enviando mensajes al topic Kafka |
| Base de datos PostgreSQL (schemas, entities, repositories) | Sin cambios |
| Redis (checkpointer, routing de conversaciones) | Sin cambios |
| Subgrafos LangGraph (topología, routers, states) | Sin cambios de estructura |
| Lógica de negocio en services Java | Sin cambios |
| DTOs existentes | Sin cambios |
| Frontend (`moustro-front`) | Sin cambios |
| AWS Bedrock / LLM config | Sin cambios |
| Kafka listener de `cliente_back` (`ChatKafkaListener`) | Sin cambios |

---

## 8. Riesgos y mitigaciones

| Riesgo | Probabilidad | Impacto | Mitigación |
|--------|-------------|---------|-----------|
| Spring Boot 4.0.3 + Spring AI 2.0.0 — posibles incompatibilidades de auto-config | Media | Alto | Spring AI 2.0 fue diseñado para Spring Boot 4.x. Verificar `./mvnw dependency:tree` para conflictos. Si hay conflictos, usar Spring Boot 3.5 + Spring AI 1.0.3 con protocolo SSE (aún soportado en 1.x) |
| Nombres de tools MCP distintos entre Java y Python | Alta | Medio | Usar `@Tool(name = "snake_case")` explícito en Java. Testear `tools/list` antes de integrar Python |
| `MultiServerMCPClient` inicialización async vs. consumer síncrono | Media | Medio | Usar `asyncio.run()` para inicialización única en arranque. El cliente es stateless en 0.3.x — no mantiene conexión persistente entre llamadas |
| Timeout en tools MCP (latencia adicional vs. HTTP directo) | Baja | Bajo | La latencia añadida es 1 hop de red adicional (dentro de Docker network). Mismos timeouts que hoy (10-20s) |
| `nodes.py` con imports hardcodeados — romper en runtime | Alta | Medio | El `tool_registry.py` lanza `ValueError` con nombre claro si el tool no existe. Fácil de detectar en el primer smoke test |
| Nodos que llaman tools en contexto síncrono | Media | Medio | `BaseTool.invoke()` soporta llamada síncrona even para tools MCP async. `langchain-mcp-adapters` maneja esto internamente |

---

## Apéndice: Decisiones de diseño

1. **Streamable-HTTP sobre SSE puro**: SSE está deprecated en Spring AI 2.0 y en la especificación MCP desde marzo 2025. Streamable-HTTP es retrocompatible con clientes SSE y es el estándar recomendado.

2. **Un solo proceso `cliente_back` con MCP embebido, no microservicio separado**: el pom.xml ya tiene todos los services. Agregar Spring AI al mismo proceso es más simple que desdoblar en dos JARs, y no hay beneficio de aislamiento porque el MCP server solo expone los services existentes.

3. **`langchain-mcp-adapters` sobre el SDK `mcp` directo**: la librería `mcp` de Anthropic requiere gestionar el ciclo de vida del cliente manualmente y construir los tools a mano. `langchain-mcp-adapters` genera directamente objetos `BaseTool` compatibles con `bind_tools()` y `ToolNode`, que es exactamente lo que usa el grafo hoy.

4. **Feature flag `USE_MCP`**: permite rollback instantáneo cambiando una variable de entorno, sin redeploy de código. Crítico en caso de incidente.

5. **No refactorizar los grafos LangGraph**: la topología de nodos es lógica de negocio independiente del protocolo de transporte. Solo el punto de inyección de tools cambia (en el arranque, no en cada nodo), y el `tool_registry.py` desacopla la resolución de tools del import estático.

---

*Content was rephrased for compliance with licensing restrictions — Sources: [Spring AI MCP Server Boot Starter](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-server-boot-starter-docs.html), [langchain-mcp-adapters PyPI 0.3.2](https://pypi.org/project/langchain-mcp-adapters/0.3.2), [Spring AI 2.0.0 GA release](https://spring.io/blog/2026/06/12/spring-ai-2-0-0-GA-available-now)*
