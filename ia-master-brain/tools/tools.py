import logging
import requests
from langchain.tools import tool
from dotenv import load_dotenv
import os

load_dotenv()

logger = logging.getLogger(__name__)
back_endpoint = os.getenv("BACK_API", "http://localhost:8080/api/v1/internet-ia")


@tool
def get_customer_info(customer_id: int) -> dict:
    """Obtiene los datos del cliente (nombre, email, teléfono, estado) dado su customer_id (solo el número, ej: 1)."""
    logger.info(f"usando herramienta en la el cliente {customer_id}")
    url = f"{back_endpoint}/customers/{customer_id}"
                           
    logger.info("[TOOL] get_customer_info(%s) -> GET %s", customer_id, url)
    try:
        response = requests.get(url, timeout=10)
        data = response.json() if response.content else {}
        logger.info("[TOOL] get_customer_info OK status=%s", response.status_code)
        return data
    except Exception as e:
        logger.error("[TOOL] get_customer_info FAILED: %s", e)
        return {"error": str(e), "status": getattr(e, "response", None) and getattr(e.response, "status_code", None)}


@tool
def get_customer_service(customer_id: str) -> dict:
    """Obtiene la lista de servicios del cliente (internet, TV, precios) dado su customer_id (solo el número, ej: 1)."""
    url = f"{back_endpoint}/customers/services/{customer_id}"
    logger.info("[TOOL] get_customer_service(%s) -> GET %s", customer_id, url)
    try:
        response = requests.get(url, timeout=10)
        data = response.json() if response.content else {}
        logger.info("[TOOL] get_customer_service OK status=%s", response.status_code)
        return data
    except Exception as e:
        logger.error("[TOOL] get_customer_service FAILED: %s", e)
        return {"error": str(e)}


@tool
def create_ticket(customer_id: int, subject: str, priority: str) -> str:
    """Crea un reclamo/ticket para el cliente. Usala cuando el usuario quiera hacer un reclamo por factura, servicio o cobro. Devuelve un mensaje con el ID del ticket para que el cliente pueda consultar en 24 horas."""
    url = f"{back_endpoint}/tickets"
    payload = {"id": None, "customerId": customer_id, "subject": subject, "priority": priority}
    logger.info("[TOOL] create_ticket(%s, %s, %s) -> POST %s", customer_id, subject, priority, url)
    try:
        response = requests.post(url, json=payload, timeout=10)
        data = response.json() if response.content else {}
        if response.status_code in (200, 201) and data.get("id"):
            ticket_id = data["id"]
            msg = f"Listo, generé un reclamo por tu factura. El ID para que consultes en 24 horas es: {ticket_id}."
            logger.info("[TOOL] create_ticket OK id=%s", ticket_id)
            return msg
        logger.error("[TOOL] create_ticket FAILED status=%s body=%s", response.status_code, data)
        return f"Error al crear el ticket: la API respondió {response.status_code}."
    except Exception as e:
        logger.error("[TOOL] create_ticket FAILED: %s", e)
        return f"Error al crear el ticket: {e}."
