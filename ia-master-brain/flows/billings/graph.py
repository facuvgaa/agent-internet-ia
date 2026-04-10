from langgraph.graph import StateGraph, END
from .nodes import (
    nodo_cargar_datos,
    nodo_conversar,
    nodo_gestionar_reclamo,
    nodo_info_servicios,
    nodo_marcar_promise,
)
from .routers import router_principal, router_servicios, router_post_carga
from .state import BillingEstate


def build_factura_graph(model_sonnet, model_haiku, checkpointer):
    workflow = StateGraph(BillingEstate)

    workflow.add_node("cargar_datos", lambda state: nodo_cargar_datos(state))
    workflow.add_node("conversar", lambda state: nodo_conversar(state, model_sonnet))
    workflow.add_node("info_servicios", lambda state: nodo_info_servicios(state, model_sonnet))
    workflow.add_node("gestionar_reclamo", lambda state: nodo_gestionar_reclamo(state, model_haiku))
    workflow.add_node("marcar_promise", lambda state: nodo_marcar_promise(state))

    workflow.set_entry_point("cargar_datos")
    workflow.add_conditional_edges(
        "cargar_datos",
        lambda state: router_post_carga(state, model_haiku),
        {
            "conversar": "conversar",
            "ir_a_promise": "marcar_promise",
            "end": END,
        }
    )

    workflow.add_conditional_edges(
        "conversar",
        lambda state: router_principal(state, model_haiku),
        {
            "info_servicios": "info_servicios",
            "gestionar_reclamo": "gestionar_reclamo",
            "conversar": "conversar",
            "end": END,
        }
    )

    workflow.add_conditional_edges(
        "info_servicios",
        lambda state: router_servicios(state, model_haiku),
        {
            "info_servicios": "info_servicios",
            "gestionar_reclamo": "gestionar_reclamo",
            "end": END,
        }
    )

    workflow.add_edge("gestionar_reclamo", "info_servicios")
    workflow.add_edge("marcar_promise", END)

    return workflow.compile(checkpointer=checkpointer)