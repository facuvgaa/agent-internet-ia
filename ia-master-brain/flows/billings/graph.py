import logging
from langchain_core.messages import HumanMessage
from langgraph.graph import StateGraph, END
from flows.billings.state import BillingEstate
from flows.billings.nodes import (
    nodo_cargar_datos,
    nodo_explicar_factura,
    nodo_detectar_intencion,
    nodo_crear_ticket,
    nodo_confirmar_ticket,
    nodo_extraer_motivo,
    nodo_pedir_detalle,
)

logger = logging.getLogger(__name__)


def build_factura_graph(model, checkpointer):

    def _router(state):            return {}
    def _cargar(state):            return nodo_cargar_datos(state, model)
    def _explicar(state):          return nodo_explicar_factura(state, model)
    def _detectar(state):          return nodo_detectar_intencion(state, model)
    def _extraer(state):           return nodo_extraer_motivo(state, model)
    def _pedir(state):             return nodo_pedir_detalle(state, model)
    def _crear(state):             return nodo_crear_ticket(state, model)
    def _confirmar(state):         return nodo_confirmar_ticket(state, model)

    # ── NUEVA: decide por dónde entra según el state restaurado de Redis ──
    def decidir_entrada(state: BillingEstate) -> str:
        tiene_datos = state.get("cliente") and state.get("facturas")
        paso = (state.get("paso_actual") or "").lower()

        if not tiene_datos:
            return "cargar_datos"           # primera vez

        if state.get("motivo_claro"):
            return "crear_ticket"           # ya tiene todo, crear ticket

        if paso == "esperando_detalle":
            return "extraer_motivo"         # cliente acaba de dar más info

        return "detectar_intencion"         # datos cargados, detectar qué quiere

    def decidir_intencion(state: BillingEstate) -> str:
        paso = (state.get("paso_actual") or "").lower()
        if paso == "reclamar":
            return "extraer_motivo"
        return END

    def decidir_motivo(state: BillingEstate) -> str:
        # si el cliente insiste, crear ticket igual
        mensajes_cliente = [
            m.content.lower() for m in state["messages"]
            if isinstance(m, HumanMessage)
        ]
        keywords_urgencia = [
            "inicia", "iniciá", "dale", "vamos",
            "cuantas veces", "ya te dije", "hermano", "ahora"
        ]
        insiste = any(
            k in msg
            for msg in mensajes_cliente
            for k in keywords_urgencia
        )
        if state.get("motivo_claro") or insiste:
            return "crear_ticket"
        return "pedir_detalle"

    builder = StateGraph(BillingEstate)

    # nodos
    builder.add_node("router_entrada",     _router)
    builder.add_node("cargar_datos",       _cargar)
    builder.add_node("explicar_facturas",  _explicar)
    builder.add_node("detectar_intencion", _detectar)
    builder.add_node("extraer_motivo",     _extraer)
    builder.add_node("pedir_detalle",      _pedir)
    builder.add_node("crear_ticket",       _crear)
    builder.add_node("confirmar_ticket",   _confirmar)

    # entrada siempre por el router
    builder.set_entry_point("router_entrada")

    # router decide por dónde continúa
    builder.add_conditional_edges(
        "router_entrada",
        decidir_entrada,
        {
            "cargar_datos":       "cargar_datos",
            "detectar_intencion": "detectar_intencion",
            "extraer_motivo":     "extraer_motivo",
            "crear_ticket":       "crear_ticket",
        }
    )

    # flechas rectas
    builder.add_edge("cargar_datos",      "explicar_facturas")
    builder.add_edge("explicar_facturas", "detectar_intencion")
    builder.add_edge("crear_ticket",      "confirmar_ticket")
    builder.add_edge("confirmar_ticket",  END)

    # bifurcaciones
    builder.add_conditional_edges(
        "detectar_intencion",
        decidir_intencion,
        {"extraer_motivo": "extraer_motivo", END: END}
    )

    builder.add_conditional_edges(
        "extraer_motivo",
        decidir_motivo,
        {"crear_ticket": "crear_ticket", "pedir_detalle": "pedir_detalle"}
    )

    builder.add_edge("pedir_detalle", END)

    # ← fix clave: el checkpointer persiste el state en Redis
    return builder.compile(checkpointer=checkpointer)


