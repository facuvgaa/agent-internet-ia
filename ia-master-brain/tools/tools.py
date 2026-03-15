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

    