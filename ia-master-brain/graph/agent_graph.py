from __future__ import annotations

import logging

from langgraph.graph import END, StateGraph

from graph.nodes import (
    billing_agent_node,
    general_agent_node,
    promise_agent_node,
    retention_agent_node,
    routing_node,
)
from graph.state import AgentState

logger = logging.getLogger(__name__)


def build_agent_graph(checkpointer):
    """
    Construye el grafo LangGraph principal sin dependencias de LangChain.

    Flujo:
      START → routing → [billing_agent | retention_agent | promise_agent | general_agent] → END

    El routing_node detecta el dominio y lo guarda en state["domain"].
    Cada nodo agente ejecuta el loop tool-use completo con boto3/Bedrock
    y los tools MCP del dominio correspondiente.
    """
    workflow = StateGraph(AgentState)

    workflow.add_node("routing", routing_node)
    workflow.add_node("billing_agent", billing_agent_node)
    workflow.add_node("retention_agent", retention_agent_node)
    workflow.add_node("promise_agent", promise_agent_node)
    workflow.add_node("general_agent", general_agent_node)

    workflow.set_entry_point("routing")

    workflow.add_conditional_edges(
        "routing",
        _route_by_domain,
        {
            "billing": "billing_agent",
            "retention": "retention_agent",
            "promise": "promise_agent",
            "general": "general_agent",
        },
    )

    workflow.add_edge("billing_agent", END)
    workflow.add_edge("retention_agent", END)
    workflow.add_edge("promise_agent", END)
    workflow.add_edge("general_agent", END)

    graph = workflow.compile(checkpointer=checkpointer)
    logger.info(
        "[AgentGraph] compilado con checkpointer: %s", type(checkpointer).__name__
    )
    return graph


def _route_by_domain(state: AgentState) -> str:
    """Función de routing condicional: retorna el nombre del nodo destino."""
    domain = state.get("domain", "general")
    if domain not in ("billing", "retention", "promise", "general"):
        return "general"
    return domain
