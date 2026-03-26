import logging
from langgraph.graph import StateGraph, END
from flows.billings.state import BillingEstate
from flows.billings.nodes import (
    nodo_cargar_datos,
    nodo_explicar_factura,
    nodo_detectar_intencion,
    nodo_crear_ticket,
    nodo_confirmar_ticket,
    nodo_extraer_motivo, nodo_pedir_detalle,
)

logger = logging.getLogger(__name__)


def build_factura_graph(model):
    
    def _cargar(state):            return nodo_cargar_datos(state, model)
    def _explicar_factura(state):  return nodo_explicar_factura(state, model)
    def _detectar_intencion(state):return nodo_detectar_intencion(state, model)
    def _extraer_motivo(state):    return nodo_extraer_motivo(state, model)
    def _pedir_detalle(state):     return nodo_pedir_detalle(state, model)
    def _crear_ticket(state):      return nodo_crear_ticket(state, model)
    def _confirmar_ticket(state):  return nodo_confirmar_ticket(state, model)

    def decidir_intencion(state: BillingEstate) -> str:
        paso = (state.get("paso_actual") or "").strip().lower()
        if paso == "reclamar":
            return "extraer_motivo" 
        return END

    def decidir_motivo(state: BillingEstate) -> str:
        if state.get("motivo_claro"):
            return "crear_ticket"
        return "pedir_detalle"

    builder = StateGraph(BillingEstate)

    builder.add_node("cargar_datos",        _cargar)
    builder.add_node("explicar_facturas",   _explicar_factura)
    builder.add_node("detectar_intencion",  _detectar_intencion)
    builder.add_node("extraer_motivo",      _extraer_motivo)
    builder.add_node("pedir_detalle",       _pedir_detalle)
    builder.add_node("crear_ticket",        _crear_ticket)
    builder.add_node("confirmar_ticket",    _confirmar_ticket)

    builder.set_entry_point("cargar_datos")

    builder.add_edge("cargar_datos",      "explicar_facturas")
    builder.add_edge("explicar_facturas", "detectar_intencion")
    builder.add_edge("crear_ticket",      "confirmar_ticket")
    builder.add_edge("confirmar_ticket",  END)

    builder.add_conditional_edges(
        "detectar_intencion",
        decidir_intencion,
        {
            "extraer_motivo": "extraer_motivo",  
            END: END
        }
    )

    builder.add_conditional_edges(
        "extraer_motivo",
        decidir_motivo,
        {
            "crear_ticket":  "crear_ticket",
            "pedir_detalle": "pedir_detalle"
        }
    )

    builder.add_edge("pedir_detalle", END)

    return builder.compile()



