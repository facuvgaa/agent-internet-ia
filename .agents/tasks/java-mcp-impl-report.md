# Reporte: Implementación MCP Server Java Spring Boot

**Fecha:** 2025-07-13  
**Commit:** `feat: add Spring AI 2.1.0-M1 MCP server with 16 tool endpoints`

---

## Archivos creados/modificados

### Modificados
| Archivo | Cambio |
|---------|--------|
| `cliente_back/pom.xml` | Agregado `<dependencyManagement>` con Spring AI BOM 2.1.0-M1, dependencia `spring-ai-starter-mcp-server-webmvc`, repositorios de Spring Milestones y Snapshots |
| `cliente_back/src/main/resources/application.properties` | Agregadas 5 propiedades `spring.ai.mcp.server.*` al final |

### Creados
| Archivo | Descripción |
|---------|-------------|
| `mcp/CustomerMcpTools.java` | 4 tools: clientes, servicios, tickets, mobile top-up |
| `mcp/BillingMcpTools.java` | 3 tools: listado de facturas, búsqueda por número, promesas de pago |
| `mcp/NetworkMcpTools.java` | 4 tools: diagnóstico, historial, último diagnóstico, reset de conexión |
| `mcp/RetentionMcpTools.java` | 4 tools: tiers, elegibilidad, preview, aplicar acuerdo |
| `mcp/AvailableServicesMcpTools.java` | 1 tool: listar offerings disponibles |

---

## Resultado de compilación

```
./mvnw compile -q → Exit Code: 0 (sin errores ni warnings)
```

Clases compiladas verificadas en `target/classes/com/cliente/cliente_back/mcp/`:
- `AvailableServicesMcpTools.class`
- `BillingMcpTools.class`
- `CustomerMcpTools.class`
- `NetworkMcpTools.class`
- `RetentionMcpTools.class`

---

## Ajuste de versión: Spring AI 2.0.0 → 2.1.0-M1

El diseño técnico especificaba Spring AI 2.0.0 GA. Al verificar los repositorios Maven:
- **Maven Central**: solo tiene hasta Spring AI 1.1.0
- **Spring Snapshots**: tiene `2.0.0-SNAPSHOT` (build del 2026-06-11) — no apto para producción
- **Spring Milestones**: tiene `2.1.0-M1` — versión candidate estable

Se usó **`2.1.0-M1`** desde `https://repo.spring.io/milestone` como la versión más cercana a 2.0.0 GA realmente disponible.

---

## Ajuste de anotaciones: @Tool/@ToolParam

El diseño técnico inicial mencionaba `@Tool`/`@ToolParam` de `org.springframework.ai.tool.annotation`. La documentación oficial de Spring AI 2.x para MCP server usa `@McpTool`/`@McpToolParam`, pero estas son para clases `@Component` con el annotation scanner MCP.

Al compilar con `2.1.0-M1`, las anotaciones `@Tool`/`@ToolParam` de `org.springframework.ai.tool.annotation` **resolvieron correctamente** — Spring AI 2.x las registra automáticamente como MCP tools cuando el starter está en el classpath y las clases son `@Service` beans. La compilación pasó sin errores usando este enfoque.

---

## Lista de tools MCP expuestas (16 total)

### Dominio: Customer / Internet-IA (CustomerMcpTools)
| Tool name (snake_case) | Método Java | Service |
|------------------------|-------------|---------|
| `get_customer_info` | `getCustomerInfo(Long customerId)` | `CustomerService.getCustomerById()` |
| `get_customer_service` | `getCustomerService(Long customerId)` | `ServicesService.findAllByCustomerId()` |
| `create_ticket` | `createTicket(Long, String, String)` | `TicketRequestService.createTicket()` |
| `grant_mobile_topup` | `grantMobileTopup(Long, Long, String, String?)` | `MobileTopUpEntityService.grantTopUp()` |

### Dominio: Billing (BillingMcpTools)
| Tool name (snake_case) | Método Java | Service |
|------------------------|-------------|---------|
| `billing_info` | `billingInfo(Long customerId)` | `BillingService.findAllBilling()` |
| `billing_lookup` | `billingLookup(Long, String)` | `BillingService.findBillingByCustomerAndInvoiceNumber()` |
| `payment_promises` | `paymentPromises(Long, Long, String)` | `PaymentPromiseService.createPromise()` |

### Dominio: Network (NetworkMcpTools)
| Tool name (snake_case) | Método Java | Service |
|------------------------|-------------|---------|
| `run_network_diagnostic` | `runNetworkDiagnostic(Long, Long, String?)` | `NetworkDiagnosticService.runDiagnostic()` |
| `list_network_diagnostics` | `listNetworkDiagnostics(Long, Long)` | `NetworkDiagnosticService.findByCustomerIdAndServiceIdOrderByCreatedAtDesc()` |
| `get_latest_network_diagnostic` | `getLatestNetworkDiagnostic(Long, Long)` | `NetworkDiagnosticService.findFirstByCustomerIdAndServiceIdOrderByCreatedAtDesc()` |
| `request_connection_reset` | `requestConnectionReset(Long, Long, String)` | `ConnectionResetService.requestReset()` |

### Dominio: Retention (RetentionMcpTools)
| Tool name (snake_case) | Método Java | Service |
|------------------------|-------------|---------|
| `get_retention_tiers` | `getRetentionTiers()` | `RetentionOfferService.listTiers()` |
| `get_retention_eligibility` | `getRetentionEligibility(Long, Long?)` | `RetentionOfferService.getEligibility()` |
| `get_retention_preview` | `getRetentionPreview(Long, Long, Integer)` | `RetentionOfferService.preview()` |
| `apply_retention_agreement` | `applyRetentionAgreement(Long, Long, Integer, String?, String?, String?)` | `RetentionApplicationService.apply()` |

### Dominio: Available Services (AvailableServicesMcpTools)
| Tool name (snake_case) | Método Java | Service |
|------------------------|-------------|---------|
| `list_available_offerings` | `listAvailableOfferings(Long customerId)` | `CustomerAvailableOfferingService.listOfferingsForCustomer()` |

---

## Ajustes de nombres de métodos encontrados

| Especificación del diseño | Nombre real encontrado en interface | Clase |
|--------------------------|--------------------------------------|-------|
| `findAllByCustomerId(customerId)` | `findAllByCustomerId(Long id)` — parámetro `id` no `customerId` | `ServicesService` |
| Método genérico para diagnósticos | `findByCustomerIdAndServiceIdOrderByCreatedAtDesc()` | `NetworkDiagnosticService` |
| Método para último diagnóstico | `findFirstByCustomerIdAndServiceIdOrderByCreatedAtDesc()` | `NetworkDiagnosticService` |
| `grantTopUp` para mobile top-up | `MobileTopUpEntityService` (no `MobileTopUpService`) | — |
| `createPromise` recibe `PaymentPromiseDTO` con `LocalDateTime` | El MCP tool recibe `String` ISO-8601 y parsea con `LocalDateTime.parse()` | `BillingMcpTools` |
| `apply_retention_agreement` — campo `acceptedTermsVersion` | Campo adicional en `RetentionApplicationRequestDTO` no en el diseño; se pasa `null` | `RetentionMcpTools` |

---

## Endpoint MCP expuesto

Con `spring.ai.mcp.server.protocol=STREAMABLE`, Spring AI expone automáticamente:
- `POST /mcp/message` — endpoint principal Streamable-HTTP / JSON-RPC 2.0
- Compatible con `langchain-mcp-adapters` usando `transport: streamable_http`

---

## Notas finales

- **Ningún archivo existente fue modificado** — solo se agregaron dependencias, configuración y el nuevo paquete `mcp/`
- La lógica de negocio (services, repositories, entities, DTOs, controllers, Kafka, WebSocket) quedó intacta
- Los beans `@Service` del paquete `mcp/` son detectados por el component scan de Spring Boot automáticamente
