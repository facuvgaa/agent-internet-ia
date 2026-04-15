from langchain_core.messages import HumanMessage
from langgraph.graph import StateGraph, END
from .nodes import (
    nodo_cargar_datos,
    nodo_generar_oferta,
    nodo_negociar,
    nodo_aplicar_retencion,
)
from .routers import router_cargar, router_oferta, router_negociacion
from .state import RetentionState


def _dispatcher(state: RetentionState, model_haiku) -> str:
    """
    Cuando ya hay ofertas y el flujo está en negociación, decide ANTES de que
    nodo_negociar corra: si el cliente aceptó → aplicar, rechazó → end, sigue → negociar.
    Así evitamos el loop nodo_negociar → router → continua → nodo_negociar...
    """
    paso = state.get("paso_actual", "")
    mensajes = state.get("messages", [])

    if state.get("ofertas_preview") and paso == "negociando":
        humanos = [m for m in mensajes if isinstance(m, HumanMessage)]
        if humanos:
            decision = router_negociacion(state, model_haiku)
            if decision == "aplicar":
                return "aplicar"
            if decision == "end":
                return "end"
        return "negociar"

    return "cargar_datos"


def build_retention_graph(model_sonnet, model_haiku, checkpointer=None):
    workflow = StateGraph(RetentionState)

    workflow.add_node("dispatcher",    lambda state: {})
    workflow.add_node("cargar_datos",   lambda state: nodo_cargar_datos(state))
    workflow.add_node("generar_oferta", lambda state: nodo_generar_oferta(state))
    workflow.add_node("negociar",       lambda state: nodo_negociar(state, model_sonnet))
    workflow.add_node("aplicar",        lambda state: nodo_aplicar_retencion(state))

    workflow.set_entry_point("dispatcher")

    workflow.add_conditional_edges(
        "dispatcher",
        lambda state: _dispatcher(state, model_haiku),
        {"cargar_datos": "cargar_datos", "negociar": "negociar", "aplicar": "aplicar", "end": END},
    )

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

    # Después de negociar siempre END — el dispatcher maneja el próximo mensaje
    workflow.add_edge("negociar", END)
    workflow.add_edge("aplicar", END)

    return workflow.compile(checkpointer=checkpointer)
