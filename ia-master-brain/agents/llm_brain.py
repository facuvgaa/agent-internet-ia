from __future__ import annotations
import logging
from typing import TypedDict, Annotated
from dotenv import load_dotenv
from langchain_core.messages import BaseMessage, HumanMessage, SystemMessage
from langgraph.graph import StateGraph, END
from langgraph.graph.message import add_messages
from langgraph.prebuilt import ToolNode
from memory.memory_brain import get_checkpointer, get_memory, save_conversation
from connection_llm.llm_conecction import get_bedrock_model_brain as llm_brain
from tools import ALL_BRAIN_TOOLS
from flows.billings.graph import build_factura_graph

from context_llm.contexts import agent_facturacion


load_dotenv()
logger = logging.getLogger(__name__)


class AgentState(TypedDict):
    messages: Annotated[list[BaseMessage], add_messages]
    metadata: dict
    dialog: dict

class LlmBrain:
    def __init__(self) -> None:
        self.tools = ALL_BRAIN_TOOLS
        self.model = llm_brain().bind_tools(self.tools)
        self.workflow = None
        self.checkpointer = get_checkpointer()
        self.factura_graph = None

    @staticmethod
    def _route(state:AgentState)->str:
        last = state["messages"][-1]

        return "tools" if getattr(last, "tool_calls", None) else END


    def brain(self) -> None:
        tool_node = ToolNode(self.tools)

        graph = StateGraph(AgentState)
        graph.add_node("assistance", self._assistance_node)
        graph.add_node("tools", tool_node)
        graph.set_entry_point("assistance")
        graph.add_conditional_edges(
            "assistance",
            self._route,
            {"tools": "tools", END: END}
        )
        graph.add_edge("tools", "assistance")

        self.workflow = graph.compile(checkpointer=self.checkpointer)

        self.factura_graph = build_factura_graph(self.model)  # ← nuevo

        logger.info("Brain listo con tools y subgrafos.")

    
    def _assistance_node(self, state: AgentState) -> dict:
        customer_id = state["metadata"].get("customer_id", "desconocido")
        system = SystemMessage(content= agent_facturacion)
        response = self.model.invoke([system, *state["messages"]])
        return {"messages": [response]}

    def run(self, input_text: str, customer_id: str):
        if not self.workflow:
            raise RuntimeError("Brain no inicializado; llamá a brain() primero.")
        keywords_factura = [
            "factura", "cobro", "pago", "deuda",
            "monto", "precio", "caro", "alto"
        ]
        es_factura = any(
            k in input_text.lower()
            for k in keywords_factura
        )

        if es_factura and self.factura_graph:
            logger.info("[BRAIN] derivando a subgrafo factura")

            state = {
                "customer_id": int(customer_id),
                "cliente":     None,
                "facturas":    None,
                "ticket_id":   None,
                "paso_actual": None,
                "messages":    [HumanMessage(content=input_text)]
            }

            config = {"configurable": {"thread_id": str(customer_id)}}
            return self.factura_graph.invoke(state, config=config)

        logger.info("[BRAIN] derivando a flujo general")

        memory = get_memory(customer_id)
        initial_state: AgentState = {
            "messages": (
                [*memory, HumanMessage(content=input_text)]
                if memory
                else [HumanMessage(content=input_text)]
            ),
            "metadata": {
                "source": "kafka",
                "customer_id": str(customer_id)
            },
            "dialog": {},
        }

        config = {"configurable": {"thread_id": str(customer_id)}}
        result_state = self.workflow.invoke(initial_state, config=config)

        try:
            save_conversation(str(customer_id))
        except Exception as e:
            logger.warning(
                "No se pudo guardar la conversación: %s", e
            )

        return result_state

