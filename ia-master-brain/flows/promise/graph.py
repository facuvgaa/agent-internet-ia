from langgraph.graph import StateGraph, END

from .nodes import (
    nodo_cargar_datos,
    nodo_ejecutar_promesa,
    nodo_explicacion_promesa
)
from .routers import router_explicacion
from .state import PromiseEstate


def build_promice_graph(model_sonnet, model_haiku, checkpointer=None):
    workflow = StateGraph(PromiseEstate)

    workflow.add_node("cargar_datos", lambda state: nodo_cargar_datos(state))
    workflow.add_node("explicacion_promesa", lambda state: nodo_explicacion_promesa(state, model_sonnet))
    workflow.add_node("ejecutar_promesa", lambda state: nodo_ejecutar_promesa(state, model_haiku))

    workflow.set_entry_point("cargar_datos")
    workflow.add_edge("cargar_datos", "explicacion_promesa")

    workflow.add_conditional_edges(
        "explicacion_promesa",
        lambda state: router_explicacion(state, model_haiku),
        {
            "explicacion_promesa": "explicacion_promesa",
            "ejecutar_promesa": "ejecutar_promesa",
            "end": END,
        }
    )

    workflow.add_edge("ejecutar_promesa", END)

    return workflow.compile(checkpointer=checkpointer)