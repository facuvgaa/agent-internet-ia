from langchain_core.messages import HumanMessage
from langgraph.graph import StateGraph, END
from .nodes import (
    nodo_cargar_datos,
    nodo_conversar,
    nodo_gestionar_reclamo,
    nodo_info_servicios,
    nodo_marcar_promise,
    nodo_marcar_retention,
)
from .routers import router_principal, router_servicios
from .state import BillingEstate


def _dispatcher(state: BillingEstate) -> str:
    """Salta directo al nodo correcto según el contexto guardado, sin re-ejecutar cargar_datos."""
    mensajes = state.get("messages", [])
    ultimo_es_humano = mensajes and isinstance(mensajes[-1], HumanMessage)
    paso = state.get("paso_actual", "")

    if paso == "info_servicios" and ultimo_es_humano:
        return "info_servicios"
    if paso == "esperando_datos_reclamo" and ultimo_es_humano:
        return "gestionar_reclamo"
    return "cargar_datos"


def build_factura_graph(model_sonnet, model_haiku, checkpointer):
    workflow = StateGraph(BillingEstate)

    workflow.add_node("dispatcher",    lambda state: {})
    workflow.add_node("cargar_datos",  lambda state: nodo_cargar_datos(state))
    workflow.add_node("conversar",     lambda state: nodo_conversar(state, model_sonnet))
    workflow.add_node("info_servicios", lambda state: nodo_info_servicios(state, model_sonnet))
    workflow.add_node("gestionar_reclamo", lambda state: nodo_gestionar_reclamo(state, model_haiku))
    workflow.add_node("marcar_promise",    lambda state: nodo_marcar_promise(state))
    workflow.add_node("marcar_retention",  lambda state: nodo_marcar_retention(state))

    workflow.set_entry_point("dispatcher")
    workflow.add_conditional_edges(
        "dispatcher",
        _dispatcher,
        {
            "cargar_datos":      "cargar_datos",
            "info_servicios":    "info_servicios",
            "gestionar_reclamo": "gestionar_reclamo",
        },
    )
    workflow.add_edge("cargar_datos", "conversar")

    workflow.add_conditional_edges(
        "conversar",
        lambda state: router_principal(state, model_haiku),
        {
            "info_servicios":    "info_servicios",
            "gestionar_reclamo": "gestionar_reclamo",
            "ir_a_promise":      "marcar_promise",
            "ir_a_retention":    "marcar_retention",
            "end":               END,
        }
    )

    workflow.add_conditional_edges(
        "info_servicios",
        lambda state: router_servicios(state, model_haiku),
        {
            "gestionar_reclamo": "gestionar_reclamo",
            "ir_a_retention":    "marcar_retention",
            "end":               END,
        }
    )

    workflow.add_conditional_edges(
        "gestionar_reclamo",
        lambda state: "end" if state.get("paso_actual") in ("esperando_datos_reclamo", "reclamo_procesado") else "info_servicios",
        {"info_servicios": "info_servicios", "end": END},
    )
    workflow.add_edge("marcar_promise",   END)
    workflow.add_edge("marcar_retention", END)

    return workflow.compile(checkpointer=checkpointer)