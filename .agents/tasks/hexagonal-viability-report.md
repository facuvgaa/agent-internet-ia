# Reporte de Viabilidad: Refactor a Arquitectura Hexagonal

**Fecha:** Julio 2025  
**Proyecto:** agent-internet-ia  
**Módulo principal analizado:** `cliente_back` (Spring Boot 4.0.3 / Java 21)

---

## Resumen ejecutivo

**El refactor es viable y recomendado, con esfuerzo MEDIO.** El backend ya tiene
separación de capas correcta (controllers → services → repositories) y lógica de dominio
real identificable. Sin embargo, las entidades JPA se usan directamente en los services como
objetos de dominio, y no existen puertos/interfaces explícitas que desacoplen el dominio del
framework. El refactor puede hacerse de forma incremental, módulo por módulo, sin
reescribir desde cero.

---

## 1. Estructura general del proyecto

El monorepo contiene tres módulos independientes orquestados por Docker Compose:

| Módulo | Tecnología | Rol |
|--------|-----------|-----|
| `cliente_back/` | Spring Boot 4.0.3, Java 21, JPA/Hibernate, Kafka, WebSocket | API REST + broker de eventos; fuente de verdad de datos de clientes |
| `ia-master-brain/` | Python 3.12, LangGraph, LangChain, AWS Bedrock, Kafka, Redis, PostgreSQL | Agente IA con grafos de conversación; consume la API del back |
| `moustro-front/` | React 19, TypeScript, Vite, Tailwind, STOMP/SockJS | UI de chat; conecta vía WebSocket al back |

Infraestructura: Kafka (KRaft), PostgreSQL ×2, Redis Stack.

---

## 2. Análisis del backend (`cliente_back`)

### 2.1 Estructura de paquetes actual

```
com.cliente.cliente_back/
├── ClienteBackApplication.java
├── config/           WebSocketConfig.java
├── controllers/      4 clases (REST + WebSocket)
├── dto/              ~22 DTOs (records e interfaces)
├── entities/         9 entidades JPA
├── mappers/          8 interfaces MapStruct
├── repositories/     9 interfaces JpaRepository
├── services/         10 interfaces + 11 implementaciones en serviceImpl/
├── kafka/            ChatKafkaListener.java
├── available/        AvailableOfferingSpec.java (enum)
└── retention/        RetentionTierSpec, RetentionApplicationStatus, RetentionOfferConstants
```

**Total: ~75 archivos Java.** Tamaño pequeño-mediano, muy manejable.

### 2.2 Capas y separación de concerns

La separación controller → service interface → service impl → repository existe y se respeta
de forma consistente. Ejemplo de cadena real:

```
InternetClaimController
  → NetworkDiagnosticService (interfaz)
    → NetworkDiagnosticServiceImpl (impl)
      → NetworkDiagnosticRepository (JpaRepository)
      → ServiceRepository (validación ownership)
```

Los controllers son limpios: solo traducen HTTP → llamada de servicio → respuesta. No hay
lógica de negocio en los controllers. Ejemplo: `InternetClaimController` inyecta 8 servicios
y cada handler tiene ≤5 líneas + manejo de excepciones.

### 2.3 Lógica de dominio identificada

Existe lógica de negocio real, concentrada en los services impl. Casos concretos:

- **Ownership validation** (`assertServiceBelongsToCustomer`): presente en
  `NetworkDiagnosticServiceImpl`, `ConnectionResetServiceImpl`, `RetentionOfferServiceImpl`,
  `RetentionApplicationServiceImpl`. Regla de negocio pura duplicada en 4 clases.

- **Cooldown de recargas móviles** (`MobileTopUpEntityServiceImpl.enforceCooldown`): lógica de
  30 días de espera entre recargas por línea. Regla de dominio clara.

- **Idempotencia de retención** (`RetentionApplicationServiceImpl.apply`): deduplicación por
  `idempotencyKey` + validación de vigencia activa. Regla de dominio pura.

- **Cálculo de precio con descuento** (`RetentionOfferServiceImpl.preview`): aritmética sobre
  `basePrice` con `RetentionTierSpec` + cashback. Lógica de negocio desacoplable.

- **Elegibilidad de offerings** (`CustomerAvailableOfferingServiceImpl`): cálculo de cupos
  activos por tipo de servicio usando `AvailableOfferingSpec` (enum con specs embebidas).

- **Domain specs como enums**: `RetentionTierSpec`, `AvailableOfferingSpec`,
  `RetentionOfferConstants` son lógica de dominio pura que ya vive fuera de JPA.

### 2.4 Acoplamiento con frameworks

| Aspecto | Estado actual | Deuda para hexagonal |
|---------|--------------|---------------------|
| Entidades JPA | Anotadas con `@Entity`, `@ManyToOne`, `@Table` en el paquete `entities/` | Se usan directamente en los services: `ServicesEntity service = serviceRepository.findById(...)` — esto acopla la lógica al modelo de persistencia |
| `@Service` / `@Component` | Spring annotations en todas las implementaciones | Los services impl no pueden usarse sin el contenedor Spring |
| `@Transactional` | Solo en 2 métodos (`apply`, `grantTopUp`) | Correcto; no hay sobre-uso |
| Mappers MapStruct | 8 mappers entity↔DTO bien separados | Buenos candidatos a convertirse en adaptadores |
| `BillingEntity.customerId` es `String` pero `CustomerEntity.id` es `Long` | Inconsistencia de tipos en la capa de persistencia | Señal de que las entidades JPA tienen acoplamiento accidental a detalles del sistema legado |
| `ChatController` inyecta `KafkaTemplate` directamente | Acoplamiento hard al broker | Puerto de salida fácil de extraer |
| `ChatKafkaListener` usa `@Autowired` field injection + `ObjectMapper` manual | Acoplamiento a infraestructura | Candidato a adaptador de entrada Kafka |

### 2.5 Puertos de entrada y salida identificados

**Puertos de entrada (driving ports):**
1. REST API `/api/v1/internet-ia` — `InternetClaimController` (8 operaciones)
2. REST API `/api/v1/retention` — `RetentionController` (4 operaciones)
3. REST API `/api/v1/available-services` — `AvailableServicesController` (1 operación)
4. WebSocket STOMP `/app/chat` — `ChatController`
5. Kafka consumer `consultas.usuario` — `ChatKafkaListener` (actúa como receptor de mensajes del agente IA)

**Puertos de salida (driven ports):**
1. PostgreSQL via JPA (9 repositories)
2. Kafka producer `respuestas.agente` — `KafkaTemplate` en `ChatController`
3. WebSocket push `/user/{id}/queue/chat` — `SimpMessagingTemplate` en `ChatKafkaListener`

**Total: 5 puertos de entrada, 3 puertos de salida.** Volumen bajo, ideal para hexagonal.

### 2.6 Tests existentes

`AllApisIntegrationTest.java`: test de integración completo con `@SpringBootTest`,
`@AutoConfigureMockMvc`, H2 en memoria, `@Transactional`. Cubre los 3 controllers REST con
~20 casos. No hay unit tests de los services por separado.

Esto es un riesgo menor del refactor: los tests actuales testean a través de HTTP, lo que
hace posible refactorizar internamente sin romper los tests, pero no ofrecen cobertura de la
lógica de dominio aislada.

---

## 3. Otros módulos

### `ia-master-brain/` (Python)

No necesita refactor hexagonal. Está estructurado como un pipeline de agente con
`flows/` (grafos LangGraph por dominio), `tools/` (adaptadores HTTP al back), `memory/`,
`kafka/`. El acoplamiento al back es solo via HTTP (`requests` en `tools/tools.py`), lo que
ya actúa como adaptador. Si `cliente_back` mantiene los mismos endpoints tras el refactor,
este módulo no se toca.

### `moustro-front/` (React/TypeScript)

No necesita refactor. Se conecta al back únicamente por WebSocket STOMP (`/ws`). Sin
dependencia directa de la estructura interna del back.

---

## 4. Evaluación de viabilidad

### 4.1 Factores a favor

1. **Interfaces de servicio ya existen**: `BillingService`, `CustomerService`, etc. son
   interfaces que sus controllers ya usan. Son los puertos de entrada naturales.

2. **DTOs bien definidos**: 22 DTOs/records cubren todos los contratos de API. No hay
   entidades JPA expuestas directamente en los controllers.

3. **MapStruct ya implementado**: los 8 mappers existentes son exactamente el patrón de
   "anti-corruption layer" entre dominio y persistencia. Solo hay que reposicionarlos.

4. **Lógica de dominio nucleada**: las reglas de negocio reales están en los services impl,
   no dispersadas en controllers ni repositories.

5. **Enums de dominio**: `RetentionTierSpec`, `AvailableOfferingSpec` son objetos de valor
   de dominio puros — no tienen ninguna dependencia de Spring ni JPA.

6. **Cobertura de integración**: los tests de HTTP existentes actúan como red de seguridad
   durante el refactor.

### 4.2 Riesgos y dificultades

1. **Entidades JPA usadas como objetos de dominio**: el mayor acoplamiento. En
   `RetentionApplicationServiceImpl`, `MobileTopUpEntityServiceImpl` y otros se crea la
   entidad JPA directamente y se setean sus campos. Solución: introducir objetos de dominio
   planos (POJOs o records) y delegar la construcción de entidades al adaptador de
   persistencia.

2. **Duplicación de `assertServiceBelongsToCustomer`**: esta regla aparece en 4 services.
   En hexagonal debe vivir una sola vez en el dominio. Es un refactor de extracción simple.

3. **`BillingEntity.customerId` como `String`**: inconsistencia de tipos que sugiere una
   adaptación ad-hoc al sistema legado de facturación. En el dominio debería ser `Long`.
   Requiere ajuste del adaptador de persistencia de billing.

4. **`ChatController` acoplado a `KafkaTemplate`**: es un driver port (WebSocket) que
   también actúa como driven port (Kafka). Necesita separación en dos responsabilidades.

5. **Sin unit tests de dominio**: después del refactor habrá un core de dominio sin
   framework Spring, y los tests actuales no lo cubren directamente. Habrá que escribir
   nuevos unit tests para el core.

6. **`@Transactional` en services**: al mover la lógica al core de dominio, `@Transactional`
   debe quedar en el adaptador de persistencia (o en un caso de uso que lo orqueste).
   Requiere atención para no perder garantías transaccionales.

---

## 5. Estimación de esfuerzo

| Tarea | Esfuerzo |
|-------|---------|
| Definir paquetes hexagonales (`domain/`, `application/`, `infrastructure/`) | Bajo (1 día) |
| Extraer objetos de valor de dominio (eliminar JPA del core) | Medio (3–4 días) |
| Definir interfaces de puerto para repositorios | Bajo (1 día) |
| Crear adaptadores de persistencia (wrapper de JpaRepository) | Medio (2–3 días) |
| Mover lógica de negocio duplicada al dominio | Bajo (1 día) |
| Refactorizar adaptadores de entrada (REST controllers, Kafka, WebSocket) | Bajo (1–2 días) |
| Escribir unit tests del core de dominio | Medio (2–3 días) |
| **Total estimado** | **~12–15 días de desarrollo** |

**Esfuerzo global: MEDIO.** El proyecto es pequeño y la separación de capas existente reduce
significativamente el costo.

---

## 6. Estructura hexagonal recomendada

```
com.cliente.cliente_back/
├── domain/
│   ├── model/          # Customer, Service, Billing, Ticket, etc. (POJOs/records sin JPA)
│   ├── port/
│   │   ├── in/         # CustomerUseCase, BillingUseCase, RetentionUseCase, etc.
│   │   └── out/        # CustomerRepository, BillingRepository, MessagePublisher, etc.
│   └── service/        # Lógica de negocio pura (sin @Service Spring)
├── application/
│   └── usecase/        # Orquestadores de casos de uso (con @Service, @Transactional)
└── infrastructure/
    ├── persistence/    # Entidades JPA, JpaRepositories, implementaciones de port/out
    ├── web/            # @RestController, @Controller WebSocket
    ├── messaging/      # KafkaTemplate wrapper, KafkaListener
    └── config/         # WebSocketConfig, KafkaConfig, BeanConfig
```

---

## 7. Recomendación

**Hacer el refactor, de forma incremental por agregado de dominio.** El orden recomendado:

1. **Retención** (más complejo, mayor lógica de negocio, listo para extraerse): empezar aquí
   maximiza el aprendizaje del patrón en el dominio más rico.
2. **Customer + Services** (núcleo del dominio compartido por todos los flujos).
3. **Billing + PaymentPromise** (acoplado a datos de sistema legado, manejar con cuidado el
   `String`/`Long` de customerId).
4. **Ticket + MobileTopUp + Diagnóstico de red + ConnectionReset** (operaciones simples,
   refactor mecánico).
5. **Adaptadores de infraestructura** (Kafka, WebSocket): último, porque dependen del dominio
   ya estabilizado.

El refactor **no requiere cambios en los endpoints REST ni en el protocolo WebSocket**, lo que
significa que `ia-master-brain` y `moustro-front` no se modifican en ninguna etapa.

Los tests de integración existentes en `AllApisIntegrationTest` sirven como red de seguridad
durante todo el proceso.

---

*Reporte generado por análisis estático del código fuente. Archivos revisados: pom.xml,
todos los .java en src/main/ y src/test/, docker-compose.yml, requirements.txt,
ia-master-brain/kafka/consumer.py, ia-master-brain/agents/llm_brain.py,
ia-master-brain/tools/tools.py, ia-master-brain/flows/retention/graph.py,
moustro-front/package.json.*
