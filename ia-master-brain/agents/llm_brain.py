from __future__ import annotations

import logging
from typing import Annotated, TypedDict
from dotenv import load_dotenv
from langchain_core.messages import AIMessage, BaseMessage, HumanMessage, SystemMessage
from langgraph.graph import StateGraph, END
from langgraph.graph.message import add_messages
from langgraph.prebuilt import ToolNode
from memory.memory_brain import get_checkpointer, get_memory, save_conversation
from connection_llm.llm_conecction import get_bedrock_model_brain as llm_brain
from tools import create_ticket,get_customer_service,get_customer_info

from context_llm.contexts import agent_facturacion

load_dotenv()
logger = logging.getLogger(__name__)



class AgentState(TypedDict):
    messages: Annotated[list[BaseMessage], add_messages]
    metadata: dict
    dialog: dict




class LlmBrain:
    def __init__(self) -> None:
        self.tools = [create_ticket,get_customer_service,get_customer_info]
        self.model = llm_brain().bind_tools(self.tools)
        self.workflow = None
        self.checkpointer = get_checkpointer()

    def _assistance_node(self, state:AgentState)-> dict:
        customer_id = state["metadata"].get("customer_id")
        system_text = agent_facturacion()
        if customer_id:
            system_text  += (
                f"\n\nEl customer_id actual es {customer_id}. "
            "Usá ese valor directamente en todas las tools. "
            "No se lo pidas al usuario."        
            )

        msgs = [SystemMessage(content=system_text), *state["messages"]]
        response = self.model.invoke(msgs)

        return {"messages":[response]}



    @staticmethod
    def _route(state:AgentState)-> str:
        last = state["messages"][-1]

        return "tools" if getattr (last, "tool_calls", None) else END



    def brain(self) -> None:
        tool_node = ToolNode(self.tools)

        graph = StateGraph(AgentState)
        
        graph.add_node("assistance", self._assistance_node)

        graph.add_node("tools", tool_node)

        
        graph.set_entry_point("assistance")

        graph.add_conditional_edges(
            "assistance",
            self._route,
            {"tools":"tools", END:END}
        )
        
        graph.add_edge("tools", "assistance")
        
        self.workflow = graph.compile(checkpointer=self.checkpointer)
        
        logger.info("Brain listo con tools.")


    def run(self, input_text: str, customer_id: str):
        """Ejecuta una interacción usando customer_id como thread_id."""
        if not self.workflow:
            raise RuntimeError("Brain no inicializado; llamá a brain() primero.")
        
        memory = get_memory(customer_id)
        
        initial_state: AgentState = {
            "messages": [*memory, HumanMessage(content=input_text)] if memory else [HumanMessage(content=input_text)],
            "metadata": {"source": "kafka", "customer_id": str(customer_id)},
            "dialog": {},
        }

        config = {"configurable": {"thread_id": str(customer_id)}}
        result_state = self.workflow.invoke(initial_state, config=config)

        try:
            save_conversation(str(customer_id))
        except Exception as e:
            logger.warning("No se pudo guardar la conversación en memoria histórica: %s", e)

        return result_state

