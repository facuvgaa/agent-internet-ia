# Mounstro v3 — Agente de Atención al Cliente con IA

Sistema de atención al cliente conversacional para una empresa de telecomunicaciones. Integra un chat en tiempo real (WebSocket/STOMP), un agente LLM orquestado con LangGraph y tres flujos de trabajo especializados e independientes, con memoria de corto y largo plazo.

**Repositorio:** https://github.com/facuvgaa/agent-internet-ia

---

## Diagrama de arquitectura

![Diagrama de arquitectura](imagen/diagrama.png)

---

## Arquitectura general

```
┌─────────────────┐   WebSocket/STOMP   ┌────────────────────────┐
│  moustro-front  │ ◄──────────────────► │    cliente_back        │
│  (React + Vite) │                      │  (Spring Boot :8080)   │
└─────────────────┘                      └───────────┬────────────┘
                                                     │  Kafka
                                          ┌──────────▼────────────┐
                                          │    ia-master-brain    │
                                          │                       │
                                          │  ┌─── Triage (Haiku)  │
                                          │  │   CONSULTA │ RECLAMO│
                                          │  │            ▼       │
                                          │  │       LlmBrain     │
                                          │  │    ┌──────────────┐ │
                                          │  │    │   billing    │ │
                                          │  │    │   promise    │ │
                                          │  │    │   retention  │ │
                                          │  │    └──────────────┘ │
                                          └──────────┬────────────┘
                                                     │
                              ┌──────────────────────┼──────────────────────┐
                         Redis (corto plazo)    Postgres ×2           AWS Bedrock
                         checkpointer +         internet-db +          (Claude)
                         ruteo activo           conversation-db
                                                (largo plazo)
```

### Flujo de un mensaje

1. El usuario escribe en el **chat (React)** → publica en `/app/chat` por STOMP.
2. **Spring Boot** publica el mensaje en Kafka topic `consultas.usuario`.
3. **ia-master-brain** consume el mensaje:
   - Si es la **primera vez** del cliente → **Haiku hace triage** (`CONSULTA` o `RECLAMO`).
   - Si ya tiene ruta guardada en Redis → va directo sin triage.
4. Las `CONSULTA` son respondidas por Haiku directamente (preguntas generales sin acceso a datos).
5. Los `RECLAMO` van al `LlmBrain` que invoca el subgrafo LangGraph correspondiente.
6. El subgrafo llama a las APIs REST de Spring Boot (facturas, tickets, retención, etc.).
7. La respuesta se publica en Kafka `respuestas.agente`.
8. Spring Boot la entrega al frontend por WebSocket (`/user/{customerId}/queue/chat`).

---

## Sistema de triaje (consumer)

El consumer tiene dos caminos completamente separados antes de invocar el agente completo:

```python
# Primera vez → triage con Haiku (liviano, sin estado)
CONSULTA → Haiku responde directamente (sin herramientas, sin LangGraph)
RECLAMO  → set_route("BRAIN") → LlmBrain (LangGraph + tools)

# Mensajes siguientes → ruta ya guardada en Redis (sin volver a clasificar)
route == "BRAIN"    → procesar_brain()
route == "CONSULTA" → responder_consulta()
```

Esto evita correr el agente completo para preguntas simples como "¿cuál es el horario de atención?" y reduce significativamente la latencia y el costo.

---

## Memoria de corto y largo plazo

El sistema implementa una estrategia de memoria en dos niveles:

### Corto plazo — Redis (sesión activa)

- **Checkpointer LangGraph** en Redis almacena el estado completo de cada subgrafo (mensajes, `paso_actual`, datos del cliente, ofertas, etc.) con el thread_id `{flujo}-{customer_id}`.
- **Ruteo activo** en Redis `db=1` con TTL de 24 hs: sabe si el cliente está en `BRAIN` o `CONSULTA` sin necesidad de reclasificar en cada mensaje.
- Si Redis no está disponible, cae automáticamente a `MemorySaver` (en memoria del proceso).

### Largo plazo — PostgreSQL (`conversation-db`)

- Al finalizar una conversación general, los mensajes se **persisten en Postgres** (`conversation_history`).
- En la próxima sesión, si Redis ya no tiene el estado, se **recuperan los últimos 5 mensajes** de Postgres como contexto inicial.
- Esto da continuidad entre sesiones: el agente "recuerda" interacciones anteriores del cliente.

```
Nueva sesión
     ↓
¿Redis tiene estado?  →  Sí → usar directamente (sesión caliente)
     ↓ No
¿Postgres tiene historial? → Sí → cargar últimos 5 mensajes como contexto
     ↓ No
Conversación nueva sin historial
```

---

## Flujos LangGraph — Independientes y conectados

Los tres subgrafos son **completamente independientes**: cada uno tiene su propio estado (`TypedDict`), sus nodos, sus routers y su checkpointer en Redis con thread_id separado. Sin embargo, **se pueden conectar entre sí** mediante el estado del grafo de billing:

```
LlmBrain (orquestador)
    │
    ├── billing ──► [paso_actual = "ir_a_promise"]  ──► promise
    │                [paso_actual = "ir_a_retention"] ──► retention
    │
    ├── promise  (entrada directa o desde billing)
    │
    └── retention (entrada directa, desde billing, o desde info_servicios)
```

Cada subgrafo termina siempre en `END` y preserva su estado en Redis. El orquestador decide en cada mensaje a cuál subgrafo derivar verificando el `paso_actual` de cada uno.

### Subgrafo Billing

Gestiona facturas, reclamos de pagos y derivaciones.

```
dispatcher → cargar_datos → conversar
                                ├─ [reclamo]     → gestionar_reclamo → END
                                ├─ [servicios]   → info_servicios
                                │                      ├─ [retention] → marcar_retention → END
                                │                      └─ [end]       → END
                                ├─ [ir_a_promise]  → marcar_promise → END
                                ├─ [ir_a_retention] → marcar_retention → END
                                └─ END
```

El `dispatcher` evita re-ejecutar `cargar_datos` si el cliente ya está en medio de un reclamo (`esperando_datos_reclamo`) o consultando servicios (`info_servicios`).

### Subgrafo Retention

Negocia descuentos y aplica acuerdos de retención.

```
dispatcher → cargar_datos → generar_oferta → negociar → END
    │              │               │              │
    │         (eligibility)  (preview por    (LLM negocia
    │                          servicio)      con cliente)
    │
    ├─ [acepta] → aplicar → END   (aplica todos los servicios de ofertas_preview)
    └─ [rechaza] → END
```

El `dispatcher` detecta aceptación/rechazo **antes** de llamar a `nodo_negociar`, evitando loops. Al aceptar, aplica directamente todas las ofertas de `ofertas_preview` sin depender del LLM para extraer service_ids.

### Subgrafo Promise

Gestiona promesas de pago para reactivar servicios.

```
cargar_datos → explicacion_promesa → ejecutar_promesa → END
```

El router `router_explicacion` espera confirmación explícita del cliente antes de ejecutar la promesa.

---

## Servicios

| Servicio | Tecnología | Puerto |
|---|---|---|
| `moustro-front` | React + Vite + nginx | 80 |
| `cliente_back` | Spring Boot 3 / Java 21 | 8080 |
| `ia_brain` | Python 3.12 + LangGraph | — (worker) |
| `kafka` | Confluent Kafka 7.5 | 9092 |
| `postgres` (`internet-db`) | PostgreSQL 15 | 5432 |
| `postgrest` (`conversation-db`) | PostgreSQL 15 | 5433 |
| `redis` | Redis Stack | 6379 |

---

## Prerrequisitos

- [Docker](https://docs.docker.com/get-docker/) y [Docker Compose](https://docs.docker.com/compose/) v2
- Credenciales de **AWS Bedrock** con acceso a los modelos `claude-3-haiku` y `claude-sonnet-4-6`

---

## Primeros pasos

### 1. Clonar el repositorio

```bash
git clone https://github.com/facuvgaa/agent-internet-ia.git
cd agent-internet-ia
```

### 2. Configurar variables de entorno

```bash
cp .env.example .env
```

Editá `.env` y completá las credenciales AWS:

```env
AWS_ACCESS_KEY_ID=tu_access_key
AWS_SECRET_ACCESS_KEY=tu_secret_key
AWS_REGION=us-east-1
```

### 3. Levantar todo

```bash
docker compose up --build
```

El primer build tarda varios minutos (Maven descarga dependencias, npm compila). Los siguientes son más rápidos gracias al cache de capas Docker.

Accedé al chat en: **http://localhost:5173/**

### Desarrollo local (sin Docker para el brain)

```bash
# Infraestructura + backend
docker compose up kafka postgres postgrest redis cliente_back

# Brain en local
cd ia-master-brain
pip install -r ../requirements.txt
python -m kafka.consumer
```

---

## Variables de entorno

| Variable | Descripción | Default |
|---|---|---|
| `AWS_ACCESS_KEY_ID` | Credencial AWS | — |
| `AWS_SECRET_ACCESS_KEY` | Credencial AWS | — |
| `AWS_SESSION_TOKEN` | Solo para credenciales temporales | — |
| `AWS_REGION` | Región de Bedrock | `us-east-1` |
| `AWS_PRIMARY_LLM` | Modelo Haiku (triaje/routing) | `anthropic.claude-3-haiku-20240307-v1:0` |
| `AWS_SECOND_LLM` | Modelo Sonnet (conversación) | `us.anthropic.claude-sonnet-4-6` |
| `BACK_API` | URL base API internet-ia | `http://localhost:8080/api/v1/internet-ia` |
| `BACK_RETENTION_API` | URL base API retención | `http://localhost:8080/api/v1/retention` |
| `BACK_SERVICE_API` | URL base API servicios disponibles | `http://localhost:8080/api/v1/available-services` |
| `CONVERSATION_DB` | PostgreSQL conversaciones | `postgresql://...@localhost:5433/conversation-db` |
| `MEMORY_REDIS_URL` | Redis checkpointer LangGraph | `redis://localhost:6379` |
| `KAFKA_BOOTSTRAP` | Servidores Kafka | `localhost:9092` |

---

## Tools disponibles para el agente

| Tool | Descripción |
|---|---|
| `get_customer_info` | Datos del cliente |
| `get_customer_service` | Servicios contratados y descuentos activos |
| `billing_info` | Facturas del cliente |
| `billing_lookup` | Buscar factura por número |
| `create_ticket` | Crear ticket de reclamo |
| `payment_promises` | Registrar promesa de pago |
| `grant_mobile_topup` | Recarga de crédito móvil |
| `request_connection_reset` | Reinicio de conexión |
| `run_network_diagnostic` | Ejecutar diagnóstico de red |
| `list_network_diagnostics` | Historial de diagnósticos |
| `get_latest_network_diagnostic` | Último diagnóstico disponible |
| `get_retention_tiers` | Niveles de descuento disponibles (1-4) |
| `get_retention_eligibility` | Elegibilidad para retención por cliente/servicio |
| `get_retention_preview` | Preview de oferta antes de aplicar |
| `apply_retention_agreement` | Aplicar acuerdo de retención |
| `list_available_offerings` | Servicios disponibles para contratar |

---

## APIs REST (`cliente_back`)

### `/api/v1/internet-ia`

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/customers/{customerId}` | Datos del cliente |
| GET | `/customers/services/{customerId}` | Servicios activos |
| GET | `/billing/customer/{customerId}` | Facturas |
| GET | `/billing/customer/{customerId}/lookup?invoiceNumber=` | Buscar factura |
| POST | `/tickets` | Crear ticket de reclamo |
| POST | `/payment-promises` | Registrar promesa de pago |
| POST | `/mobile-topups` | Recarga móvil |
| POST | `/connection-resets` | Reinicio de conexión |
| POST | `/network-diagnostics` | Ejecutar diagnóstico |
| GET | `/network-diagnostics/customers/{id}/services/{sid}` | Historial diagnósticos |
| GET | `/network-diagnostics/customers/{id}/services/{sid}/latest` | Último diagnóstico |

### `/api/v1/retention`

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/tiers` | Niveles de descuento disponibles |
| GET | `/customers/{customerId}/eligibility` | Elegibilidad global o `?serviceId=` |
| POST | `/preview` | Preview de oferta sin aplicar |
| POST | `/applications` | Aplicar acuerdo de retención |

### `/api/v1/available-services`

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/customers/{customerId}/offerings` | Servicios disponibles para contratar |

### WebSocket

- **Endpoint:** `ws://localhost:8080/ws` (SockJS)
- **Enviar mensaje:** `/app/chat` con `{ contenido: string, customerId: string }`
- **Recibir respuesta:** `/user/{customerId}/queue/chat`

---

## Estructura del proyecto

```
mounstrov3/
├── docker-compose.yml
├── .env.example
├── requirements.txt
├── imagen/
│   └── diagrama.png
│
├── ia-master-brain/
│   ├── Dockerfile
│   ├── agents/
│   │   └── llm_brain.py           # Orquestador principal (routing + subgrafos)
│   ├── flows/
│   │   ├── billings/              # Flujo facturación y reclamos
│   │   │   ├── graph.py
│   │   │   ├── nodes.py
│   │   │   ├── routers.py
│   │   │   ├── prompts.py
│   │   │   └── state.py
│   │   ├── retention/             # Flujo negociación de descuentos
│   │   │   ├── graph.py
│   │   │   ├── nodes.py
│   │   │   ├── routers.py
│   │   │   ├── promps.py
│   │   │   └── state.py
│   │   └── promise/               # Flujo promesa de pago
│   │       ├── graph.py
│   │       ├── nodes.py
│   │       ├── routers.py
│   │       └── promps.py
│   ├── kafka/
│   │   └── consumer.py            # Entry point: triage + loop Kafka
│   ├── tools/
│   │   └── tools.py               # 16 tools LangChain → APIs REST
│   ├── context_llm/
│   │   └── contexts.py            # Prompts del agente Emma
│   ├── memory/
│   │   └── memory_brain.py        # Checkpointer Redis + historial Postgres
│   └── connection_llm/
│       └── llm_conecction.py      # Clientes Bedrock (Haiku / Sonnet)
│
├── cliente_back/
│   ├── Dockerfile
│   └── src/main/java/com/cliente/ # Controllers, Services, Repositories, JPA
│
└── moustro-front/
    ├── Dockerfile
    ├── nginx.conf                 # SPA + proxy /ws → Spring Boot
    └── src/
        ├── App.tsx                # Componente principal del chat
        └── hooks/
            └── useChat.ts         # Hook WebSocket/STOMP
```

---

## Comandos útiles

```bash
# Levantar todo
docker compose up --build

# Solo infraestructura
docker compose up kafka postgres postgrest redis

# Ver logs del brain en tiempo real
docker logs -f ia-master-brain

# Limpiar memoria Redis y Postgres para testing
docker exec redis_memory redis-cli FLUSHALL
docker exec postgrest-agent psql -U admin-agent -d internet-db \
  -c "TRUNCATE TABLE tickets, retention_applications, payment_promises RESTART IDENTITY CASCADE;"
docker exec conversations_db psql -U admin-llm -d conversation-db \
  -c "TRUNCATE TABLE conversation_history RESTART IDENTITY CASCADE;"

# Reconstruir solo un servicio
docker compose build ia_brain
docker compose up --no-deps -d ia_brain
```

---

## Licencia

MIT License — Copyright (c) 2026 Facundo Vega

Se permite el uso, copia, modificación, fusión, publicación, distribución, sublicencia y/o venta del software, siempre que se incluya este aviso de copyright en todas las copias o partes sustanciales del software.

El software se proporciona "tal cual", sin garantía de ningún tipo.
