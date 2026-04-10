import logging
from typing import Literal
from langchain_core.messages import HumanMessage, SystemMessage
from .state import BillingEstate
from .prompts import ROUTE_SERVICIOS_PROMPT, ROUTE_PROMESA_PROMPT, ROUTE_PRINCIPAL_PROMPT

logger = logging.getLogger(__name__)


def _ai_ofrecio_promesa(mensajes: list) -> bool:
    for m in reversed(mensajes):
        if isinstance(m, HumanMessage):
            break
        if "promesa de pago" in getattr(m, "content", "").lower():
            return True
    return False


def router_post_carga(state: BillingEstate, model_haiku) -> Literal["ir_a_promise", "conversar", "end"]:
    mensajes = state.get("messages", [])
    if not mensajes:
        return "conversar"

    ultimo_es_humano = isinstance(mensajes[-1], HumanMessage)
    por_estado = state.get("paso_actual") == "oferta_promesa_enviada"
    por_historial = _ai_ofrecio_promesa(mensajes)

    if ultimo_es_humano and (por_estado or por_historial):
        logger.info("[router_post_carga] oferta detectada, evaluando respuesta del cliente")
        return router_oferta_promesa(state, model_haiku)

    if not ultimo_es_humano and por_estado:
        return "end"

    return "conversar"


def router_oferta_promesa(state: BillingEstate, model_haiku) -> Literal["ir_a_promise", "conversar", "end"]:
    mensajes = state.get("messages", [])
    humanos = [m for m in mensajes if isinstance(m, HumanMessage)]
    if not humanos:
        return "end"

    contenido = humanos[-1].content
    prompt = ROUTE_PROMESA_PROMPT.format(mensaje=contenido)
    resultado = model_haiku.invoke([SystemMessage(content=prompt)])
    decision = resultado.content.strip().lower()

    if "acepta" in decision:
        return "ir_a_promise"
    if "rechaza" in decision:
        return "end"
    return "conversar"


def router_principal(state: BillingEstate, model_haiku) -> Literal["info_servicios", "gestionar_reclamo", "derivar_promise", "end"]:
    mensajes = state.get("messages", [])
    if not mensajes:
        return "end"

    humanos = [m for m in mensajes if isinstance(m, HumanMessage)]
    if not humanos:
        return "end"

    contenido = humanos[-1].content
    prompt = ROUTE_PRINCIPAL_PROMPT.format(mensaje=contenido)
    resultado = model_haiku.invoke([SystemMessage(content=prompt)])
    decision = resultado.content.strip().lower()

    logger.info("[router_principal] decision haiku='%s' mensaje='%s'", decision, contenido)

    if decision == "reclamo":
        return "gestionar_reclamo"
    if decision == "servicios":
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