import logging
import os
from typing import Any
import requests
from dotenv import load_dotenv
from langchain.tools import tool

load_dotenv()

logger = logging.getLogger(__name__)

back_endpoint = os.getenv("BACK_API", "http://localhost:8080/api/v1/internet-ia")
back_retention_endpoint = os.getenv("BACK_RETENTION_API", "http://localhost:8080/api/v1/retention")
back_service_endpoint = os.getenv("BACK_SERVICE_API", "http://localhost:8080/api/v1/available-services")


def _safe_json(response: requests.Response) -> Any:
    if not response.content:
        return {}
    try:
        return response.json()
    except Exception:
        return {"raw": response.text}


# --- internet-ia ---


@tool
def get_customer_info(customer_id: int) -> dict:
    """Datos del cliente (nombre, email, teléfono, estado)."""
    url = f"{back_endpoint}/customers/{customer_id}"
    logger.info("[TOOL] get_customer_info(%s) -> GET %s", customer_id, url)
    try:
        response = requests.get(url, timeout=10)
        data = _safe_json(response)
        logger.info("[TOOL] get_customer_info OK status=%s", response.status_code)
        return {"status_code": response.status_code, "data": data}
    except Exception as e:
        logger.error("[TOOL] get_customer_info FAILED: %s", e)
        return {"error": str(e)}


@tool
def get_customer_service(customer_id: int) -> dict:
    """Servicios/líneas contratadas del cliente (planes, dirección, precios)."""
    url = f"{back_endpoint}/customers/services/{customer_id}"
    logger.info("[TOOL] get_customer_service(%s) -> GET %s", customer_id, url)
    try:
        response = requests.get(url, timeout=10)
        data = _safe_json(response)
        logger.info("[TOOL] get_customer_service OK status=%s", response.status_code)
        return {"status_code": response.status_code, "data": data}
    except Exception as e:
        logger.error("[TOOL] get_customer_service FAILED: %s", e)
        return {"error": str(e)}


@tool
def create_ticket(customer_id: int, subject: str, priority: str) -> dict:
    """Abre un ticket de reclamo técnico. Devuelve el id y estado del ticket."""
    url = f"{back_endpoint}/tickets"
    payload = {"id": None, "customerId": customer_id, "subject": subject, "priority": priority}
    logger.info("[TOOL] create_ticket(%s, %s, %s) -> POST %s", customer_id, subject, priority, url)
    try:
        response = requests.post(url, json=payload, timeout=10)
        data = _safe_json(response)
        if response.status_code in (200, 201) and isinstance(data, dict) and data.get("id"):
            logger.info("[TOOL] create_ticket OK id=%s", data["id"])
            return {
                "success":   True,
                "ticket_id": str(data["id"]),
            }
        logger.error("[TOOL] create_ticket FAILED status=%s body=%s", response.status_code, data)
        return {
            "success":   False,
            "ticket_id": None,
            "error":     f"API respondió {response.status_code}"
        }
    except Exception as e:
        logger.error("[TOOL] create_ticket FAILED: %s", e)
        return {
            "success":   False,
            "ticket_id": None,
            "error":     str(e)
        }


@tool
def billing_info(customer_id: int) -> dict:
    """Facturas / estado de cuenta del cliente (lista de BillingDTO)."""
    url = f"{back_endpoint}/billing/customer/{customer_id}"
    logger.info("[TOOL] billing_info(%s) -> GET %s", customer_id, url)
    try:
        response = requests.get(url, timeout=10)
        data = _safe_json(response)
        logger.info("[TOOL] billing_info OK status=%s", response.status_code)
        return {"status_code": response.status_code, "data": data}
    except Exception as e:
        logger.error("[TOOL] billing_info FAILED: %s", e)
        return {"error": str(e)}


@tool
def billing_lookup(customer_id: int, invoice_number: str) -> dict:
    """
    Busca una factura del cliente por número: id interno (solo dígitos), número impreso en la factura
    o etiqueta de período. El texto puede venir de OCR/visión a partir de una foto de la factura.
    """
    url = f"{back_endpoint}/billing/customer/{customer_id}/lookup"
    logger.info(
        "[TOOL] billing_lookup(%s, %r) -> GET %s",
        customer_id,
        invoice_number,
        url,
    )
    try:
        response = requests.get(
            url, params={"invoiceNumber": invoice_number}, timeout=10
        )
        data = _safe_json(response)
        logger.info("[TOOL] billing_lookup OK status=%s", response.status_code)
        return {"status_code": response.status_code, "data": data}
    except Exception as e:
        logger.error("[TOOL] billing_lookup FAILED: %s", e)
        return {"error": str(e)}


@tool
def payment_promises(customer_id: int, billing_id: int, promise_until: str) -> str:
    """
    Registra compromiso de pago sobre una factura.
    billing_id: id de factura (de billing_info). promise_until: ISO-8601 futuro.
    """
    url = f"{back_endpoint}/payment-promises"
    payload = {
        "id": None,
        "customerId": customer_id,
        "billingId": billing_id,
        "promiseUntil": promise_until,
        "status": None,
    }
    logger.info(
        "[TOOL] payment_promises(%s, %s, %s) -> POST %s",
        customer_id,
        billing_id,
        promise_until,
        url,
    )
    try:
        response = requests.post(url, json=payload, timeout=10)
        data = _safe_json(response)
        if response.status_code in (200, 201) and isinstance(data, dict) and data.get("id"):
            hasta = data.get("promiseUntil") or promise_until
            msg = (
                "Listo, generé el compromiso de pago. Si querés puedo reiniciar tu módem desde acá "
                f"y nos aseguramos de que ande; recordá pagar antes del {hasta}."
            )
            logger.info("[TOOL] payment_promises OK id=%s promiseUntil=%s", data.get("id"), hasta)
            return msg
        logger.error("[TOOL] payment_promises FAILED status=%s body=%s", response.status_code, data)
        return f"Error al crear la promesa de pago: la API respondió {response.status_code}."
    except Exception as e:
        logger.error("[TOOL] payment_promises FAILED: %s", e)
        return f"Error al crear la promesa de pago: {e}."


@tool
def grant_mobile_topup(customer_id: int, ticket_id: int, msisdn: str, reason: str = "") -> str:
    """
    Compensación 10 GB móvil ligada a un ticket de falla técnico.
    msisdn: número normalizado; debe coincidir con el teléfono del cliente si está cargado.
    """
    url = f"{back_endpoint}/mobile-topups"
    payload: dict = {
        "customerId": customer_id,
        "ticketId": ticket_id,
        "msisdn": msisdn,
    }
    if reason:
        payload["reason"] = reason
    logger.info("[TOOL] grant_mobile_topup(%s, %s) -> POST %s", customer_id, ticket_id, url)
    try:
        response = requests.post(url, json=payload, timeout=15)
        data = _safe_json(response)
        if response.status_code == 201 and isinstance(data, dict):
            return (
                f"Aplicada compensación de {data.get('dataGb', 10)} GB. "
                f"Beneficio/ticket: {data.get('benefitId')}. Mensaje: {data.get('message', '')}"
            )
        logger.error("[TOOL] grant_mobile_topup FAILED status=%s body=%s", response.status_code, data)
        return f"No se pudo aplicar la recarga: HTTP {response.status_code}."
    except Exception as e:
        logger.error("[TOOL] grant_mobile_topup FAILED: %s", e)
        return f"Error al aplicar recarga: {e}."


@tool
def request_connection_reset(customer_id: int, service_id: int, reason: str) -> dict:
    """Encola reset de conexión (CPE/OSS) para una línea del cliente."""
    url = f"{back_endpoint}/connection-resets"
    payload = {"customerId": customer_id, "serviceId": service_id, "reason": reason}
    logger.info("[TOOL] request_connection_reset(%s, %s) -> POST %s", customer_id, service_id, url)
    try:
        response = requests.post(url, json=payload, timeout=15)
        data = _safe_json(response)
        logger.info("[TOOL] request_connection_reset status=%s", response.status_code)
        return {"status_code": response.status_code, "data": data}
    except Exception as e:
        logger.error("[TOOL] request_connection_reset FAILED: %s", e)
        return {"error": str(e)}


@tool
def run_network_diagnostic(customer_id: int, service_id: int, channel: str = "IA") -> dict:
    """Ejecuta diagnóstico de red para la línea indicada."""
    url = f"{back_endpoint}/network-diagnostics"
    payload = {"customerId": customer_id, "serviceId": service_id, "channel": channel}
    logger.info("[TOOL] run_network_diagnostic(%s, %s) -> POST %s", customer_id, service_id, url)
    try:
        response = requests.post(url, json=payload, timeout=20)
        data = _safe_json(response)
        logger.info("[TOOL] run_network_diagnostic status=%s", response.status_code)
        return {"status_code": response.status_code, "data": data}
    except Exception as e:
        logger.error("[TOOL] run_network_diagnostic FAILED: %s", e)
        return {"error": str(e)}


@tool
def list_network_diagnostics(customer_id: int, service_id: int) -> dict:
    """Lista diagnósticos previos de esa línea (más reciente primero)."""
    url = (
        f"{back_endpoint}/network-diagnostics/customers/{customer_id}/services/{service_id}"
    )
    logger.info("[TOOL] list_network_diagnostics -> GET %s", url)
    try:
        response = requests.get(url, timeout=10)
        data = _safe_json(response)
        return {"status_code": response.status_code, "data": data}
    except Exception as e:
        logger.error("[TOOL] list_network_diagnostics FAILED: %s", e)
        return {"error": str(e)}


@tool
def get_latest_network_diagnostic(customer_id: int, service_id: int) -> dict:
    """Último diagnóstico de red para la línea."""
    url = (
        f"{back_endpoint}/network-diagnostics/customers/{customer_id}/services/"
        f"{service_id}/latest"
    )
    logger.info("[TOOL] get_latest_network_diagnostic -> GET %s", url)
    try:
        response = requests.get(url, timeout=10)
        data = _safe_json(response)
        return {"status_code": response.status_code, "data": data}
    except Exception as e:
        logger.error("[TOOL] get_latest_network_diagnostic FAILED: %s", e)
        return {"error": str(e)}


# --- retention ---


@tool
def get_retention_tiers() -> dict:
    """Catálogo de niveles de retención (1–4: % descuento y meses)."""
    url = f"{back_retention_endpoint}/tiers"
    logger.info("[TOOL] get_retention_tiers -> GET %s", url)
    try:
        response = requests.get(url, timeout=10)
        data = _safe_json(response)
        return {"status_code": response.status_code, "data": data}
    except Exception as e:
        logger.error("[TOOL] get_retention_tiers FAILED: %s", e)
        return {"error": str(e)}


@tool
def get_retention_eligibility(customer_id: int, service_id: int | None = None) -> dict:
    """
    Indica si el cliente puede cotizar retención y qué niveles aplican.
    service_id opcional: id de línea en services para validar pertenencia.
    """
    url = f"{back_retention_endpoint}/customers/{customer_id}/eligibility"
    if service_id is not None:
        url = f"{url}?serviceId={service_id}"
    logger.info("[TOOL] get_retention_eligibility -> GET %s", url)
    try:
        response = requests.get(url, timeout=10)
        data = _safe_json(response)
        return {"status_code": response.status_code, "data": data}
    except Exception as e:
        logger.error("[TOOL] get_retention_eligibility FAILED: %s", e)
        return {"error": str(e)}


@tool
def get_retention_preview(customer_id: int, service_id: int, level: int) -> dict:
    """Simula precio/texto de retención para un nivel 1–4 (no guarda nada)."""
    url = f"{back_retention_endpoint}/preview"
    payload = {"customerId": customer_id, "serviceId": service_id, "level": level}
    logger.info("[TOOL] get_retention_preview level=%s -> POST %s", level, url)
    try:
        response = requests.post(url, json=payload, timeout=10)
        data = _safe_json(response)
        return {"status_code": response.status_code, "data": data}
    except Exception as e:
        logger.error("[TOOL] get_retention_preview FAILED: %s", e)
        return {"error": str(e)}


@tool
def apply_retention_agreement(
    customer_id: int,
    service_id: int,
    level: int,
    idempotency_key: str = "",
    channel: str = "IA",
    notes: str = "",
) -> str:
    """
    Registra en BD el acuerdo de retención (después de que el cliente aceptó).
    level 1–4; % y meses los define el servidor.
    """
    url = f"{back_retention_endpoint}/applications"
    payload: dict[str, Any] = {
        "customerId": customer_id,
        "serviceId": service_id,
        "level": level,
    }
    if idempotency_key:
        payload["idempotencyKey"] = idempotency_key
    if channel:
        payload["channel"] = channel
    if notes:
        payload["notes"] = notes
    logger.info("[TOOL] apply_retention_agreement -> POST %s", url)
    try:
        response = requests.post(url, json=payload, timeout=15)
        data = _safe_json(response)
        if response.status_code == 201 and isinstance(data, dict):
            return (
                f"Retención registrada (id aplicación {data.get('applicationId')}). "
                f"Nivel {data.get('level')}: {data.get('discountPercent')}% por {data.get('durationMonths')} meses. "
                f"Vigencia hasta {data.get('validUntil', '')}."
            )
        logger.error("[TOOL] apply_retention_agreement FAILED status=%s body=%s", response.status_code, data)
        return f"No se pudo registrar la retención: HTTP {response.status_code}."
    except Exception as e:
        logger.error("[TOOL] apply_retention_agreement FAILED: %s", e)
        return f"Error al registrar retención: {e}."


# --- available-services (add-ons) ---


@tool
def list_available_offerings(customer_id: int) -> dict:
    """Catálogo de add-ons contratables y si el cliente puede adherir (cupos por tipo)."""
    url = f"{back_service_endpoint}/customers/{customer_id}/offerings"
    logger.info("[TOOL] list_available_offerings(%s) -> GET %s", customer_id, url)
    try:
        response = requests.get(url, timeout=10)
        data = _safe_json(response)
        return {"status_code": response.status_code, "data": data}
    except Exception as e:
        logger.error("[TOOL] list_available_offerings FAILED: %s", e)
        return {"error": str(e)}


# Lista única para el grafo del agente
ALL_BRAIN_TOOLS = [
    get_customer_info,
    get_customer_service,
    create_ticket,
    billing_info,
    billing_lookup,
    payment_promises,
    grant_mobile_topup,
    request_connection_reset,
    run_network_diagnostic,
    list_network_diagnostics,
    get_latest_network_diagnostic,
    get_retention_tiers,
    get_retention_eligibility,
    get_retention_preview,
    apply_retention_agreement,
    list_available_offerings,
]
