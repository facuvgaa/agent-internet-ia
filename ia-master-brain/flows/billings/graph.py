import logging
from langgraph.graph import StateGraph, END
from flows.billings.state import BillingEstate
from flows.billings.nodes import (
    nodo_cargar_datos,
    nodo_explicar_factura,
    nodo_detectar_intencion,
    nodo_crear_ticket,
    nodo_confirmar_ticket,
)

logger = logging.getLogger(__name__)


def build_factura_graph(model):
    
    def _cargar(state): return nodo_cargar_datos(state, model)
    def _explicar_factura(state): return nodo_explicar_factura(state, model)
    def _detectar_intencion(state): return nodo_detectar_intencion(state, model)
    def _crear_ticket(state): return nodo_crear_ticket(state, model)
    def _confirmar_ticket(state): return nodo_confirmar_ticket(state, model)


    def decidir(state: BillingEstate)-> str:
        paso = state.get("paso_actual", "")
        if paso == "reclamar": return "crear_ticket"
        if paso == "pagar":    return END   
        if paso == "cerrado":  return END
        return END

    builder = StateGraph(BillingEstate)


    builder.add_node("cargar_datos", _cargar)
    builder.add_node("explicar_facturas", _explicar_factura)
    builder.add_node("detectar_intencion", _detectar_intencion)
    builder.add_node("crear_ticket", _crear_ticket)
    builder.add_node("confirmar_ticket", _confirmar_ticket)


    builder.set_entry_point("cargar_datos")

    builder.add_edge("cargar_datos", "explicar_facturas")
    builder.add_edge("explicar_facturas", "detectar_intencion")
    builder.add_edge("detectar_intencion", "crear_ticket")
    builder.add_edge("crear_ticket", "confirmar_ticket")
    builder.add_edge("confirmar_ticket", END)


    builder.add_conditional_edges = (
        "detectar_interaccion", decidir,{
            "crear_ticket": "crear_ticket",
            END:        END

        }
    )

    return builder.compile()



