from typing import Literal
from langchain_core.messages import HumanMessage
from .state import BillingEstate

def router_principal(state: BillingEstate) -> Literal["info_servicios", "gestionar_reclamo", "end"]:
    mensajes = state.get("messages", [])
    if not mensajes: return "end"

    ultimo_ai = mensajes[-1]
    if hasattr(ultimo_ai, "tool_calls") and len(ultimo_ai.tool_calls) > 0:
        return "gestionar_reclamo"

    # buscar el último mensaje del usuario para decidir la ruta
    humanos = [m for m in mensajes if isinstance(m, HumanMessage)]
    if not humanos:
        return "end"
    contenido = humanos[-1].content.lower()

    palabras_reclamo = ["reclamo", "reclamar", "queja", "no corresponde", "cobro mal", "error en",
                        "cortar", "corte", "impaga", "impago", "comprobante", "no impacto", "no se acredito"]
    if any(p in contenido for p in palabras_reclamo):
        return "gestionar_reclamo"

    if state.get("pasa_Actual") == "ir_a_servicios":
        return "info_servicios"

    palabras_servicios = ["servicio", "plan", "promo", "precio", "aumento", "caro", "por que sube", "porque sube"]
    if any(p in contenido for p in palabras_servicios):
        return "info_servicios"

    return "end"

def router_servicios(state: BillingEstate) -> Literal["gestionar_reclamo", "info_servicios", "end"]:
    if state.get("paso_actual") == "reclamo_procesado":
        return "end"

    mensajes = state.get("messages", [])
    if not mensajes: return "end"

    ultimo_ai = mensajes[-1]
    if hasattr(ultimo_ai, "tool_calls") and len(ultimo_ai.tool_calls) > 0:
        return "gestionar_reclamo"

    humanos = [m for m in mensajes if isinstance(m, HumanMessage)]
    if not humanos:
        return "end"
    contenido = humanos[-1].content.lower()

    palabras_cierre = ["listo", "gracias", "chau", "hasta luego", "no gracias", "eso es todo", "nada mas", "nada más"]
    if any(p in contenido for p in palabras_cierre):
        return "end"

    palabras_reclamo = ["reclamo", "reclamar", "queja", "no corresponde", "cobro mal", "error en",
                        "cortar", "corte", "impaga", "impago", "comprobante", "no impacto", "no se acredito"]
    if any(p in contenido for p in palabras_reclamo):
        return "gestionar_reclamo"

    return "info_servicios"