# ia-master-brain: Migración a MCP + boto3 directo

La implementación reemplaza el orquestador LangChain (subgrafos separados, `ChatBedrockConverse`, `@tool`, `BaseMessage`) por un grafo LangGraph unificado con cuatro nodos agente que llaman Bedrock directamente vía boto3 y ejecutan tools a través del MCP Python SDK. El consumer Kafka mantiene su estructura de triage/ruteo Redis sin cambios funcionales; la inicialización del cliente MCP ocurre al arranque del proceso. El código nuevo en `llm/`, `graph/`, `context_llm/`, `memory/` está limpio de dependencias LangChain. Los módulos legacy (`flows/`, `agents/`, `tools/`, `connection_llm/`) siguen en disco pero ya no son referenciados desde el path de ejecución activo.

**Watch for:** módulo físico llamado `mcp_client/` pero el diseño lo especificaba como `mcp/` — todos los imports en `consumer.py` y `nodes.py` usan `mcp_client.client`, coherente internamente, pero diverge del spec y colisiona con el namespace `mcp` del SDK (confirmed). `call_tool_sync()` resuelve correctamente el problema del event loop cross-thread usando `run_coroutine_threadsafe` en lugar de `asyncio.new_event_loop()` (confirmed). `httpx` y `anyio` — deps requeridas por el MCP SDK — no están listadas explícitamente en `requirements.txt` (confirmed).

**Verdict**: NEEDS_CHANGES

---

## High-level view

El grafo unificado (`StateGraph` con cinco nodos: `routing` + cuatro agentes) está correctamente topologiado y compila con el checkpointer. El `routing_node` lee `domain` y `paso_actual` del estado persistido para mantener conversaciones activas entre turnos, delegando la clasificación al LLM Haiku solo cuando es la primera vez o el dominio cerró. Esto replica el comportamiento de `LlmBrain.run()` anterior con menos código.

El agentic loop en `BedrockClient.run_agent_loop()` implementa correctamente el ciclo `tool_use → execute → tool_results → repeat` hasta `end_turn`, acumulando el historial completo incluyendo bloques `toolUse` y `toolResult` en formato nativo Bedrock. Los mensajes devueltos al reducer son solo los nuevos (slice desde `existing_count`), lo que funciona correctamente con `_merge_messages`.

El cliente MCP inicializa en un thread daemon con event loop propio y expone `call_tool_sync()` que despacha al loop correcto vía `run_coroutine_threadsafe` — esto evita el bug "Future attached to a different loop" que el diseño marcaba como punto crítico. Sin embargo el módulo se creó como `mcp_client/` en vez de `mcp/`, todos los imports apuntan a `mcp_client.client`, y hay que verificar que este nombre no colisione con el paquete `mcp` del SDK en el path de Python.

`memory_brain.py` eliminó completamente los imports de `langchain_core.messages` y serializa/deserializa mensajes como dicts nativos Bedrock. `save_conversation()` itera correctamente con `.get("role")` y extrae texto de los bloques `content`. El consumer usa `result_state.get("ultima_respuesta")` con fallback a iterar `messages` en reversa buscando `role == "assistant"`.

`requirements.txt` no lista `langchain`, `langchain-core` ni `langchain-aws` como dependencias directas. Faltan `httpx>=0.27.0` y `anyio>=4.0.0`, que el MCP SDK necesita para `streamable_http_client`; instalados como transitivas hoy pero sin versión pinneada es un riesgo de ruptura silenciosa.

---

<details>
<summary>Issues (4)</summary>

1. **Nombre de módulo `mcp_client` vs `mcp`** — El directorio se creó como `mcp_client/` (spec pedía `mcp/`). Todos los imports usan `mcp_client.client` y son internamente coherentes, pero el nombre `mcp` está tomado por el SDK instalado, por lo que `mcp_client/` es correcto en la práctica. Documentar la divergencia del spec o renombrar el directorio en el spec; de lo contrario el siguiente coder podría seguir el spec y romper los imports.

2. **`httpx` y `anyio` ausentes de `requirements.txt`** — `mcp.client.streamable_http.streamable_http_client` depende de `httpx` (HTTP client) y `anyio` (async runtime). Ambas llegan como transitivas del paquete `mcp` hoy, pero sin versión pinneada en requirements un upgrade del SDK puede traer versiones incompatibles sin aviso. Agregar `httpx>=0.27.0` y `anyio>=4.0.0` explícitamente.

3. **`_detect_paso()` no detecta cierre para `general`** — El dominio `general` no tiene marcadores de cierre en `_detect_paso()`. `consumer.py` chequea `paso_actual in PASOS_CERRADOS` para borrar la ruta Redis. Si el usuario resuelve en `general` y la conversación queda con `paso_actual = "respondido"`, la ruta Redis nunca se borra y el próximo mensaje del mismo cliente bypasea el triage aunque sea un contexto completamente distinto. Agregar un marcador de cierre para `general` o extender `PASOS_CERRADOS` con `"respondido"`.

4. **`requirements.txt` ubicado solo en la raíz del repo, no dentro de `ia-master-brain/`** — El Dockerfile y los procesos de build que apuntan a la raíz lo encuentran, pero si se corre el módulo de forma aislada (CI, test unitario) las dependencias pueden no resolverse. Verificar que el Dockerfile de `ia-master-brain` referencie el archivo correcto.

</details>

<details>
<summary>Details</summary>

### Nombre de módulo `mcp_client/` vs `mcp/`

El diseño técnico (`ia-brain-redesign.md`) y el plan de implementación (`brain-impl-plan.md`) especifican el módulo como `ia-master-brain/mcp/client.py`. El directorio en disco es `ia-master-brain/mcp_client/client.py`. Todos los imports en `consumer.py` y `nodes.py` usan `mcp_client.client` — confirman que el código nuevo es internamente coherente y no hay import roto en el path de ejecución activo.

El nombre `mcp_client/` probablemente se eligió para evitar colisionar con el namespace del SDK instalado (`import mcp`), lo cual es correcto. Sin embargo el spec sigue diciendo `mcp/`. Si un implementador futuro sigue el spec sin leer el código, agregará un directorio `mcp/` que shadea el SDK y rompe la inicialización de la sesión. La resolución es actualizar el spec o comentar la razón del renombrado en el `__init__.py`.

### Agentic loop en `BedrockClient.run_agent_loop()`

El loop itera hasta `max_iterations=10`. En cada vuelta llama `invoke_with_tools`, agrega el `output_message` al historial, y si `stopReason == "tool_use"` ejecuta todos los `toolUse` blocks en paralelo-lineal (uno a uno), construye el mensaje `{"role": "user", "content": tool_results}` y continúa. En `end_turn` extrae el texto final y retorna. El manejo de `stop_reason` inesperado (max_tokens, guardrail) retorna el texto parcial con `"Sin respuesta."` como fallback — degradado sin crash.

Un edge case no manejado: si Bedrock retorna `tool_use` pero `output_message["content"]` no contiene ningún bloque `toolUse` (posible en algunos modelos al llegar a límite de contexto), `tool_results` queda vacío y el siguiente turno envía `{"role": "user", "content": []}`, que la API de Bedrock rechaza con error de validación. El loop se rompe con excepción no atrapada y la respuesta nunca llega al consumer. Agregar un guard: si `tool_results` está vacío después de iterar los bloques, salir del loop con el texto parcial disponible.

### Event loop cross-thread en `mcp_client/client.py`

`call_tool_sync()` usa `asyncio.run_coroutine_threadsafe(call_tool_async(...), _mcp_loop)`, que es el patrón correcto para despachar desde un thread síncrono al loop del thread daemon MCP. El diseño señalaba este punto como crítico (bug "Future attached to a different loop") y la implementación lo resuelve correctamente. El timeout de 30 segundos en `future.result(timeout=30)` es razonable para operaciones de red.

Si `initialize_mcp()` no se llama antes de cualquier `call_tool_sync()`, `_mcp_loop is None` y se lanza `RuntimeError` con mensaje claro. El consumer lo llama al arranque antes de construir el grafo.

El timeout de 10 segundos en `initialize_mcp()` para esperar que `_tools_bedrock_cache` se pueble es un busy-wait con `time.sleep(0.1)`. Si el servidor MCP no está disponible al arranque, el timeout expira silenciosamente y `get_tools_by_domain()` retorna lista vacía — Bedrock recibirá llamadas sin `toolConfig`, responderá texto sin usar tools. El consumer logea el warning pero continúa, lo cual es el comportamiento de degradación esperado según el diseño.

### `routing_node` y mantenimiento de dominio entre turnos

En el segundo turno de una conversación activa, `routing_node` lee `domain != ""` y `paso_actual not in _PASOS_CERRADOS` y retorna `{}` sin modificar el estado — el checkpointer Redis ya tiene el dominio correcto del primer turno. La única excepción activa es el cambio `retention → billing` por palabras clave, que hace sentido de negocio (cliente que está negociando retención menciona una factura específica).

El conjunto `_PASOS_CERRADOS` en `nodes.py` es un superset de `PASOS_CERRADOS` en `consumer.py` (nodes agrega `"no_elegible"` y `"sin_ofertas"`). Esto es correcto: el routing reconoce más condiciones de cierre que el consumer, que solo borra la ruta Redis para los cierres definitivos con acción completada.

### `_detect_paso()` y coherencia con el consumer

Los dominios `billing`, `retention` y `promise` tienen marcadores de cierre específicos y coherentes con `PASOS_CERRADOS` del consumer. El dominio `general` no tiene marcador de cierre — siempre retorna `"respondido"` o `"en_progreso"`. `"respondido"` no está en `PASOS_CERRADOS` del consumer, por lo que una conversación `general` nunca borra la ruta Redis. El efecto: el siguiente mensaje del mismo cliente irá directamente al grafo (route == "BRAIN") sin retriage, incluso si la sesión terminó hace días y el nuevo mensaje es sobre un tema completamente distinto.

### `memory_brain.py` — eliminación de imports LangChain

`get_full_conversation()` lee directamente del checkpoint Redis via `cp.get_tuple(config)` y accede a `channel_values["messages"]` — este path retorna los dicts nativos del nuevo `AgentState` sin pasar por `_row_to_message`. Coherente con el diseño.

`save_conversation()` itera mensajes con `m.get("role")` y extrae texto de `m.get("content", [])` filtrando bloques `"text"` — correcto para el formato Bedrock Converse. Los mensajes con solo bloques `toolUse` o `toolResult` (sin `"text"`) producen `contenido = ""` en Postgres, lo que es razonable para el historial de largo plazo.

No hay ningún import de `langchain_core` en el módulo (confirmed).

### Topología del grafo en `agent_graph.py`

`START → routing → conditional_edges(domain) → [billing_agent | retention_agent | promise_agent | general_agent] → END`. La función `_route_by_domain()` tiene un fallback a `"general"` para valores de dominio inesperados, lo que evita un `KeyError` en el dispatcher de LangGraph.

El grafo no tiene edges de re-entrada: cada nodo agente va directo a `END`. El loop de tool-use ocurre dentro del nodo, no como edges del grafo. Esto es intencional y correcto para este diseño.

### Test coverage

No hay tests para ninguno de los módulos nuevos. Los puntos con mayor riesgo sin cobertura son: `_detect_paso()` con cada dominio, el fallback de `call_tool_sync()` cuando `_mcp_loop is None`, el comportamiento del `routing_node` en el segundo turno (dominio activo vs cerrado), y el edge case de `tool_results` vacío en `run_agent_loop()`.

</details>

---

<details>
<summary>File map</summary>

| Archivo | Cambio |
|---------|--------|
| `llm/bedrock_client.py` | Nuevo — `BedrockClient` con `invoke_simple`, `invoke_with_tools`, `run_agent_loop` vía boto3 |
| `mcp_client/client.py` | Nuevo — cliente MCP con thread daemon, `call_tool_sync` vía `run_coroutine_threadsafe` |
| `graph/state.py` | Nuevo — `AgentState` TypedDict sin LangChain, reducer `_merge_messages` |
| `graph/nodes.py` | Nuevo — `routing_node` + cuatro nodos agente + `_run_agent` + `_detect_paso` |
| `graph/agent_graph.py` | Nuevo — `build_agent_graph()` con topología correcta |
| `context_llm/contexts.py` | Modificado — consolidados cuatro prompts de sistema + `get_system_prompt(domain, customer_id)` |
| `memory/memory_brain.py` | Modificado — eliminados imports `langchain_core`, serialización a dicts nativos Bedrock |
| `kafka/consumer.py` | Modificado — reemplaza `LlmBrain` por `build_agent_graph`, inicializa MCP al arranque |
| `requirements.txt` (raíz) | Modificado — eliminados `langchain*`, agregados `mcp>=1.0.0`, `boto3`, mantenido resto |

</details>
