# Mounstro v3 — Agente de Atención al Cliente con IA

Sistema de atención al cliente conversacional basado en IA para una empresa de telecomunicaciones. Integra un chat en tiempo real (WebSocket), un agente LLM orquestado con LangGraph, y un backend Spring Boot con APIs REST.

---

## Arquitectura general

```
┌─────────────────┐     WebSocket/STOMP     ┌──────────────────────┐
│  moustro-front  │ ◄──────────────────────► │   cliente_back       │
│  (React + Vite) │                          │  (Spring Boot :8080) │
└─────────────────┘                          └──────────┬───────────┘
                                                        │ Kafka
                                             ┌──────────▼───────────┐
                                             │    ia-master-brain   │
                                             │  (Python + LangGraph)│
                                             └──────────────────────┘
                                                        │
                                       ┌────────────────┼────────────────┐
                                  Redis (estado)   Postgres ×2      AWS Bedrock
                                  (checkpointer    (internet-db +    (Claude)
                                   + ruteo)         conversation-db)
```

### Flujo de un mensaje

1. El usuario escribe en el **chat (React)** → se publica en `/app/chat` por STOMP.
2. **Spring Boot** publica el mensaje en el topic Kafka `consultas.usuario`.
3. **ia-master-brain** consume el mensaje, detecta la intención y lo enruta al subgrafo LangGraph correspondiente.
4. El subgrafo llama a las APIs REST de Spring Boot según sea necesario (facturas, retención, tickets, etc.).
5. La respuesta se publica en Kafka `respuestas.agente`.
6. Spring Boot la recibe y la envía al frontend por WebSocket (`/user/{customerId}/queue/chat`).

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
git clone <repo-url>
cd mounstrov3
```

### 2. Configurar variables de entorno

```bash
cp .env.example .env
```

Editá `.env` y completá al menos las credenciales AWS:

```env
AWS_ACCESS_KEY_ID=tu_access_key
AWS_SECRET_ACCESS_KEY=tu_secret_key
AWS_REGION=us-east-1
```

### 3. Levantar todo

```bash
docker compose up --build
```

El primer build tarda varios minutos (Maven descarga dependencias, npm compila). Los siguientes son mucho más rápidos gracias al cache de capas.

Accedé al chat en: **http://localhost**

### Desarrollo local (sin Docker para el brain)

Si preferís correr el brain directamente para desarrollo:

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

## Agente IA — Flujos LangGraph

El cerebro (`ia-master-brain`) usa **LangGraph** para orquestar conversaciones con estado persistido en Redis. Tiene un grafo principal y tres subgrafos especializados.

### Grafo principal — `LlmBrain`

Detecta la intención del usuario (`billing` / `retention` / `general`) y delega al subgrafo correspondiente. Mantiene estado de qué flujo está activo por cliente.

**Modelos:** Claude Haiku para triaje/routing (rápido y barato), Claude Sonnet para la conversación principal.

### Subgrafo Billing

Maneja todo lo relacionado con facturas y pagos.

```
dispatcher → cargar_datos → conversar
                                ├── info_servicios ──► marcar_retention → [retention]
                                ├── gestionar_reclamo (crea ticket)
                                ├── marcar_promise → [promise]
                                └── END
```

**Capacidades:**
- Mostrar detalle de facturas y estado de cuenta
- Crear tickets de reclamo (pago no impactado, cargo incorrecto, etc.)
- Derivar a promesa de pago
- Derivar a retención si el cliente pide descuentos

### Subgrafo Retention (negociación de descuentos)

Gestiona la negociación de promociones para retener clientes.

```
dispatcher → cargar_datos → generar_oferta → negociar ──► aplicar → END
                                  │                  └──► END (rechazo)
                              (eligibility
                               + preview por servicio)
```

**Lógica de negociación:**
1. Verifica elegibilidad del cliente por servicio
2. Genera ofertas para el nivel mínimo disponible (mayor al descuento actual)
3. LLM presenta la oferta y negocia con el cliente
4. Si acepta → aplica el acuerdo en todos los servicios vía API
5. Si rechaza → cierra la conversación

### Subgrafo Promise (promesa de pago)

Permite registrar una promesa de pago para reactivar servicios cortados.

```
cargar_datos → explicacion_promesa → ejecutar_promesa → END
```

### Tools disponibles

El agente tiene acceso a 16 herramientas que llaman al backend:

| Tool | Descripción |
|---|---|
| `get_customer_info` | Datos del cliente |
| `get_customer_service` | Servicios contratados |
| `billing_info` | Facturas del cliente |
| `billing_lookup` | Buscar factura por número |
| `create_ticket` | Crear ticket de reclamo |
| `payment_promises` | Registrar promesa de pago |
| `grant_mobile_topup` | Recarga de crédito móvil |
| `request_connection_reset` | Reinicio de conexión |
| `run_network_diagnostic` | Diagnóstico de red |
| `list_network_diagnostics` | Historial de diagnósticos |
| `get_latest_network_diagnostic` | Último diagnóstico |
| `get_retention_tiers` | Niveles de descuento disponibles |
| `get_retention_eligibility` | Elegibilidad para retención |
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
| GET | `/tiers` | Niveles de descuento (1-4) |
| GET | `/customers/{customerId}/eligibility` | Elegibilidad global o por `?serviceId=` |
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
├── requirements.txt               # Dependencias Python
│
├── ia-master-brain/
│   ├── Dockerfile
│   ├── agents/
│   │   └── llm_brain.py           # Orquestador principal
│   ├── flows/
│   │   ├── billings/              # Flujo facturación
│   │   │   ├── graph.py
│   │   │   ├── nodes.py
│   │   │   ├── routers.py
│   │   │   ├── prompts.py
│   │   │   └── state.py
│   │   ├── retention/             # Flujo retención/descuentos
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
│   │   └── consumer.py            # Entry point: consume y produce Kafka
│   ├── tools/
│   │   └── tools.py               # Tools LangChain → APIs REST
│   ├── context_llm/
│   │   └── contexts.py            # Prompts del agente Emma
│   ├── memory/
│   │   └── memory_brain.py        # Checkpointer Redis + historial Postgres
│   └── connection_llm/
│       └── llm_conecction.py      # Clientes Bedrock (Haiku / Sonnet)
│
├── cliente_back/
│   ├── Dockerfile
│   └── src/main/java/com/cliente/ # Controllers, Services, Repositories
│
└── moustro-front/
    ├── Dockerfile
    ├── nginx.conf
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

# Ver logs del brain
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
