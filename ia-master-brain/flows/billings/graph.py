from langchain_core.runnables import router
from langgraph.graph import StateGraph, END
from nodes import nodo_cargar_datos, nodo_conversar, nodo_gestionar_reclamo, nodo_info_servicios
from routers import router_principal, router_servicios 
from state import BillingEstate

def build_factura_graph(model_sonnet, model_haiku, checkpointer):
    workflow = StateGraph(BillingEstate)

    workflow.add_node("cargar_datos", lambda state: nodo_cargar_datos(state))
    workflow.add_node("conversar", lambda state: nodo_conversar(state, model_sonnet))
    workflow.add_node("info_servicios", lambda state: nodo_info_servicios(state, model_sonnet))

    workflow.add_node("gestionar_reclamo", lambda state: nodo_gestionar_reclamo(state, model_haiku))


    workflow.set_entry_point("cargar_datos")
    workflow.add_edge("cargar_datos", "conversar")
    workflow.add_conditional_edges(
        "conversar",
        router_principal,
        {
            "info_servicios": "info_servicios",
            "gestionar_reclamo": "gestionar_reclamo",
            "end": END
        }
    )
    workflow.add_conditional_edges(
    "info_servicios",
    router_servicios, 
    {
        "info_servicios": "info_servicios", 
        "gestionar_reclamo": "gestionar_reclamo",
        "end": END
    }
    )


    return workflow.compile(checkpointer=checkpointer)