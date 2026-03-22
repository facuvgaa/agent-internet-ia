from langgraph.graph import StateGraph, END
from flows.billings.state import FacturaState
from flows.billings.nodes import (
    nodo_cargar_datos,
    nodo_explicar_factura,
    nodo_detectar_intencion,
    nodo_crear_ticket,
    nodo_promesa_pago,
)


def build_factura_graph(model):
    """
    Construye y devuelve el subgrafo del flujo de factura.
    Recibe el modelo como parámetro para no instanciarlo dos veces.
    """

    # wrappers para inyectar el modelo en cada nodo
    def _cargar(state):     return nodo_cargar_datos(state, model)
    def _explicar(state):   return nodo_explicar_factura(state, model)
    def _detectar(state):   return nodo_detectar_intencion(state, model)
    def _ticket(state):     return nodo_crear_ticket(state, model)
    def _promesa(state):    return nodo_promesa_pago(state, model)

    # ── conditional edge: qué hacer según la intención ──
    def decidir(state: FacturaState) -> str:
        paso = state.get("paso_actual", "")
        if paso == "reclamar":  return "crear_ticket"
        if paso == "pagar":     return "promesa_pago"
        if paso == "cerrado":   return END
        return END

    builder = StateGraph(FacturaState)

    builder.add_node("cargar_datos",        _cargar)
    builder.add_node("explicar_factura",    _explicar)
    builder.add_node("detectar_intencion",  _detectar)
    builder.add_node("crear_ticket",        _ticket)
    builder.add_node("promesa_pago",        _promesa)

    builder.set_entry_point("cargar_datos")
    builder.add_edge("cargar_datos",       "explicar_factura")
    builder.add_edge("explicar_factura",   "detectar_intencion")

    builder.add_conditional_edges(
        "detectar_intencion",
        decidir,
        {
            "crear_ticket": "crear_ticket",
            "promesa_pago": "promesa_pago",
            END:            END
        }
    )

    builder.add_edge("crear_ticket",  END)
    builder.add_edge("promesa_pago",  END)

    return builder.compile()