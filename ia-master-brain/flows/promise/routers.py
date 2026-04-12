from typing import Literal
from langchain_core.messages import HumanMessage, SystemMessage
from flows.promise.state import PromiseEstate
from flows.promise.promps import ROUTE_SYSTEM_PROMICE

import logging

logger = logging.getLogger(__name__)


def router_explicacion(state: PromiseEstate, model_haiku) -> Literal["ejecutar_promesa", "explicacion_promesa", "end"]:
    
    if state.get("paso_actual") == "promesa_procesada":
        return "end"

    if not state.get("puede_prometer", True):
        return "end"

    mensajes = state.get("messages", [])

    if not mensajes or not isinstance(mensajes[-1], HumanMessage):
        return "end"

    humanos = [m for m in mensajes if isinstance(m, HumanMessage)]
    if not humanos:
        return "end"

    ultimo_humano = humanos[-1].content
    prompt = ROUTE_SYSTEM_PROMICE.format(mensaje=ultimo_humano)

    resultado = model_haiku.invoke([SystemMessage(content=prompt)])
    decision = resultado.content.strip().lower()

    logger.info("[promise router] decision haiku='%s' mensaje='%s'", decision, ultimo_humano)

    if decision == "acepta":
        return "ejecutar_promesa"
    if decision == "rechaza":
        return "end"
    return "explicacion_promesa"
