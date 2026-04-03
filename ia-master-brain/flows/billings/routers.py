from typing import Literal
from .state import BillingEstate

def router_principal(state: BillingEstate) -> Literal["info_servicios", "gestionar_reclamo", "end"]:
    mensajes = state.get("messages", [])
    if not mensajes: return "end"
    
    ultimo_mensaje = mensajes[-1]
    contenido = ultimo_mensaje.content.lower()

    if hasattr(ultimo_mensaje, "tool_calls") and len(ultimo_mensaje.tool_calls) > 0:
        return "gestionar_reclamo"

   
    palabras_servicios = ["servicio", "detalle", "por que", "porque", "caro", "promo", "plan"]
    if state.get("pasa_Actual") == "ir_a_servicios" or any(p in contenido for p in palabras_servicios):
        return "info_servicios"
    
    if "reclamo" in contenido or "reclamar" in contenido or "queja" in contenido:
        return "gestionar_reclamo"

    return "end"

def router_servicios(state: BillingEstate) -> Literal["gestionar_reclamo", "info_servicios", "end"]:
    mensajes = state.get("messages", [])
    if not mensajes: return "end"
    
    ultimo_mensaje = mensajes[-1]
    contenido = ultimo_mensaje.content.lower()

    if hasattr(ultimo_mensaje, "tool_calls") and len(ultimo_mensaje.tool_calls) > 0:
        return "gestionar_reclamo"
    
    if "reclamar" in contenido or "reclamo" in contenido or "no corresponde" in contenido:
        return "gestionar_reclamo"

    if any(p in contenido for p in ["otro", "ademas", "tambien", "y el", "y la"]):
        return "info_servicios"

    return "end"