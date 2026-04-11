import logging
from typing import Literal
from langchain_core.messages import HumanMessage, SystemMessage
from .state import BillingEstate
from .prompts import ROUTE_SERVICIOS_PROMPT, ROUTE_PRINCIPAL_PROMPT

logger = logging.getLogger(__name__)



def router_principal(state: BillingEstate, model_haiku) -> Literal["info_servicios", "gestionar_reclamo", "ir_a_promise", "end"]:
    mensajes = state.get("messages", [])
    if not mensajes:
        return "end"

    humanos = [m for m in mensajes if isinstance(m, HumanMessage)]
    if not humanos:
        return "end"

    ultimo_humano = humanos[-1].content

    ultimo_ai = ""
    for m in reversed(mensajes):
        if not isinstance(m, HumanMessage):
            ultimo_ai = getattr(m, "content", "")
            break

    prompt = ROUTE_PRINCIPAL_PROMPT.format(ultimo_ai=ultimo_ai, mensaje=ultimo_humano)
    resultado = model_haiku.invoke([SystemMessage(content=prompt)])
    decision = resultado.content.strip().lower()

    logger.info("[router_principal] decision='%s' cliente='%s'", decision, ultimo_humano)

    if "promise" in decision:
        return "ir_a_promise"
    if "reclamo" in decision:
        return "gestionar_reclamo"
    if "servicios" in decision:
        return "info_servicios"
    return "end"

def router_servicios(state: BillingEstate, model_haiku) -> Literal["gestionar_reclamo", "info_servicios", "end"]:
    if state.get("paso_actual") == "reclamo_procesado":
        return "end"

    mensajes = state.get("messages", [])
    if not mensajes:
        return "end"

    humanos = [m for m in mensajes if isinstance(m, HumanMessage)]
    if not humanos:
        return "end"

    contenido = humanos[-1].content
    prompt = ROUTE_SERVICIOS_PROMPT.format(mensaje=contenido)
    resultado = model_haiku.invoke([SystemMessage(content=prompt)])
    decision = resultado.content.strip().lower()

    if decision == "reclamo":
        return "gestionar_reclamo"
    if decision == "cierre":
        return "end"
    return "info_servicios"