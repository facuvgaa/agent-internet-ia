from langgraph.graph import StateGraph, END
from .nodes import (
    nodo_cargar_datos,
    nodo_generar_oferta,
    nodo_negociar,
    nodo_aplicar_retencion,
)
from .routers import router_cargar, router_oferta, router_negociacion
from .state import RetentionState


def build_retention_graph(model_sonnet, model_haiku, checkpointer=None):
    workflow = StateGraph(RetentionState)

    workflow.add_node("cargar_datos",   lambda state: nodo_cargar_datos(state))
    workflow.add_node("generar_oferta", lambda state: nodo_generar_oferta(state))
    workflow.add_node("negociar",       lambda state: nodo_negociar(state, model_sonnet))
    workflow.add_node("aplicar",        lambda state: nodo_aplicar_retencion(state, model_haiku))

    workflow.set_entry_point("cargar_datos")

    workflow.add_conditional_edges(
        "cargar_datos",
        router_cargar,
        {"generar_oferta": "generar_oferta", "end": END},
    )

    workflow.add_conditional_edges(
        "generar_oferta",
        router_oferta,
        {"negociar": "negociar", "end": END},
    )

    workflow.add_conditional_edges(
        "negociar",
        lambda state: router_negociacion(state, model_haiku),
        {"negociar": "negociar", "aplicar": "aplicar", "end": END},
    )

    workflow.add_edge("aplicar", END)

    return workflow.compile(checkpointer=checkpointer)
