import logging
from typing import Literal
from langchain_core.messages import HumanMessage, SystemMessage
from flows.retention.state import RetentionState
from .promps import ROUTE_NEGOCIACION

logger = logging.getLogger(__name__)


def router_cargar(state: RetentionState) -> Literal["generar_oferta", "end"]:
    if state.get("paso_actual") == "no_elegible":
        return "end"
    return "generar_oferta"


def router_oferta(state: RetentionState) -> Literal["negociar", "end"]:
    if state.get("paso_actual") == "sin_ofertas":
        return "end"
    return "negociar"


def router_negociacion(state: RetentionState, model_haiku) -> Literal["negociar", "aplicar", "end"]:
    if state.get("paso_actual") == "retencion_aplicada":
        return "end"

    mensajes = state.get("messages", [])
    if not mensajes:
        return "end"

    humanos = [m for m in mensajes if isinstance(m, HumanMessage)]
    if not humanos:
        return "end"
    ultimo_humano = humanos[-1].content

    prompt = ROUTE_NEGOCIACION.format(mensaje=ultimo_humano)
    resultado = model_haiku.invoke([SystemMessage(content=prompt)])
    decision = resultado.content.strip().lower()

    logger.info("[retention router] decision='%s' mensaje='%s'", decision, ultimo_humano)

    if decision == "acepta":
        return "aplicar"
    if decision == "rechaza":
        return "end"
    return "negociar"
