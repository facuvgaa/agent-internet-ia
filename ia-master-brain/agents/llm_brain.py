from __future__ import annotations

import logging
from typing import Annotated, TypedDict
from dotenv import load_dotenv
from langchain_core.messages import AIMessage, BaseMessage, HumanMessage
from langgraph.graph import StateGraph, END
from langgraph.graph.message import add_messages
from memory.memory_brain import get_checkpointer, get_memory, save_conversation
from connection_llm.llm_conecction import get_bedrock_model_brain as llm_brain
from tools import create_ticket,get_customer_service,get_customer_info

load_dotenv()
logger = logging.getLogger(__name__)



class AgentState(TypedDict):
    messages: Annotated[list[BaseMessage], add_messages]
    metadata: dict
    dialog: dict




class LlmBrain:
    def __init__(self) -> None:
        self.model = llm_brain()
        self.workflow = None
        self.checkpointer = get_checkpointer()

    def _placeholder_node(self, state: AgentState) -> dict:
        return {
            "messages": [
                AIMessage(
                    content=(
                        "Brain en construcción. Ya está andando la memoria (checkpointer + save_conversation). "
                        "Implementá tus nodos/tools/policies en `ia-master-brain/agents/llm_brain.py`."
                    )
                )
            ]
        }

    def _assistant_node(self, state:AgentState)->dict:

        response = self.model.invoke(state["messages"])

        return {"messages": [response]}

    def brain(self) -> None:
        graph = StateGraph(AgentState)
        graph.add_node("assistance", self._assistant_node)
        graph.set_entry_point("assistance")
        graph.add_edge("assistance", END)
        self.workflow = graph.compile(checkpointer=self.checkpointer)
        logger.info("Brain skeleton listo (solo memoria + placeholder).")

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

