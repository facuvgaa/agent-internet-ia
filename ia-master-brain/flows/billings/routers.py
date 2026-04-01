from typing import Literal
from .state import BillingEstate

def router_principal(state: BillingEstate) -> Literal["info_servicios", "gestionar_reclamo", "end"]:

    mensajes = state.get("messages", [])
    if not mensajes:
        return "end"
    
    ultimo_mensaje = mensajes[-1]

    if hasattr(ultimo_mensaje, "tool_calls") and len(ultimo_mensaje.tool_calls) > 0:
        return "gestionar_reclamo"

   
    if state.get("pasa_Actual") == "ir_a_servicios":
        return "info_servicios"

    return "end"


def router_servicios(state: BillingEstate) -> Literal["gestionar_reclamo", "info_servicios", "end"]:
    
    mensajes = state.get("messages", [])
    if not mensajes:
        return "end"
    
    ultimo_mensaje = mensajes[-1]

    if hasattr(ultimo_mensaje, "tool_calls") and len(ultimo_mensaje.tool_calls) > 0:
        return "gestionar_reclamo"
    return "end"