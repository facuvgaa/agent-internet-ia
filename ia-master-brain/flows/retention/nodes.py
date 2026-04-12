import logging
from langchain_core.messages import AIMessage, HumanMessage, SystemMessage
from flows.retention.state import RetentionState
from tools.tools import get_customer_info, get_customer_service ,get_retention_eligibility, get_retention_tiers, get_retention_preview

logger = logging.getLogger(__name__)


def nodo_cargar_datos(state: RetentionState) -> dict:
    customer_id = state["customer_id"]
    customer_info = get_customer_info.invoke({"customer_id": customer_id})
    cliente = customer_info.get("data", {})
    servicios_raw = get_customer_service.invoke({"customer_id": customer_id})
    servicios = servicios_raw.get("data", [])
    eligibility = get_retention_eligibility.invoke({"customer_id": customer_id})
    retention_nivel = eligibility.get("data", {})
    logger.info(
        "[retention] customer_id=%s eligible=%s servicios=%s",
        customer_id,
        retention_nivel.get("eligible"),
        len(servicios),
    )
    return {
        "cliente": cliente,
        "servicios": servicios,
        "eligibility": retention_nivel,
        "paso_actual": "cargar_datos",
    }


def nodo_generar_oferta(state:RetentionState, model)-> dict:

    customer_id= state["customer_id"]
    


