# DTOs y base de datos

**Regla:** no todo lo que viaja en JSON es una tabla. Los **DTO** describen entradas/salidas del API; las **Entity** son lo que JPA persiste.

## Con tabla / entidad (persistidos)

| DTO | Entidad / tabla |
|-----|-----------------|
| `CustomerDTO` | `CustomerEntity` → `customers` |
| `ServicesDTO` | `ServicesEntity` → `services` |
| `TicketRequestDTO` | `TicketRequestEntity` → `tickets` |
| `BillingDTO` | `BillingEntity` → facturación (según tu esquema) |
| `PaymentPromiseDTO` | `PaymentPromiseEntity` |
| `MobileTopUpRequest/ResponseDTO` | `MobileTopUpEntity` → `mobile_topups` |
| `ConnectionResetRequest/ResponseDTO` | `ConnectionResetEntity` |
| `NetworkDiagnosticRequest/ResponseDTO` | `NetworkDiagnosticEntity` |
| `RetentionApplicationRequest/ResponseDTO` | `RetentionApplicationEntity` → `retention_applications` |

## Sin tabla propia (calculados, catálogo en código o vistas de lectura)

| DTO | Origen |
|-----|--------|
| `RetentionTierDTO` | Catálogo fijo: `RetentionTierSpec` (enum), no es fila en BD |
| `RetentionPreviewRequest/ResponseDTO` | **Simulación**: se calcula con `ServicesEntity` + tier; no se guarda el preview |
| `RetentionEligibilityResponseDTO` | **Reglas** sobre cliente/servicio + tiers; no es entidad |
| `AvailableOfferingItemDTO` / `AvailableOfferingsForCustomerDTO` | Catálogo `AvailableOfferingSpec` + conteo de `services` del cliente |
| `AddonSubscriptionRequestDTO` | Solo **request** del futuro `POST` de adhesión; el alta crearía una fila en `services` |

## Enums (`BenefitStatus`, `BillingStatus`, etc.)

Van como **columnas** dentro de entidades, no como tablas separadas (salvo que normalices a tablas de catálogo).

## Cuándo sí crear tabla

- Necesitás **historial** o **consultas** que no salen de una sola entidad.
- El dato debe **sobrevivir** independiente del cálculo en memoria.

Ejemplos que ya están bien como tabla: `retention_applications`, `mobile_topups`, `tickets`.

## Resumen

Tener muchos DTO “sin tabla” es **normal**: son proyecciones, requests, respuestas armadas o reglas de negocio. El problema sería confundir un DTO de **solo lectura/cálculo** con una entidad y esperar `SELECT` directo; por eso este mapa.
