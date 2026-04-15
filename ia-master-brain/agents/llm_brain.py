from __future__ import annotations
import logging
from typing import TypedDict, Annotated, Literal
from dotenv import load_dotenv
from langchain_core.messages import BaseMessage, HumanMessage, SystemMessage
from langgraph.graph import StateGraph, END
from langgraph.graph.message import add_messages
from langgraph.prebuilt import ToolNode
from memory.memory_brain import get_checkpointer, get_memory, save_conversation
from connection_llm.llm_conecction import get_bedrock_model_brain as llm_brain
from connection_llm.llm_conecction import get_bedrock_model_master as llm_haiku
from tools import ALL_BRAIN_TOOLS

from flows.billings.graph import build_factura_graph
from flows.promise.graph import build_promice_graph
from flows.retention.graph import build_retention_graph
from context_llm.contexts import agent_facturacion, route_prompt

load_dotenv()
logger = logging.getLogger(__name__)

class AgentState(TypedDict):
    messages: Annotated[list[BaseMessage], add_messages]
    metadata: dict
    dialog: dict

class LlmBrain:
    def __init__(self) -> None:
        self.tools         = ALL_BRAIN_TOOLS
        self._llm_base     = llm_brain() 
        self.model         = self._llm_base.bind_tools(self.tools)
        self.model_haiku   = llm_haiku()
        self.checkpointer  = get_checkpointer()
        self.workflow      = None
        self.subgrafos     = {} 

    def brain(self) -> None:
        
        self.subgrafos = {
            "billing":   build_factura_graph(self._llm_base, self.model_haiku, self.checkpointer),
            "promise":   build_promice_graph(self._llm_base, self.model_haiku, self.checkpointer),
            "retention": build_retention_graph(self._llm_base, self.model_haiku, self.checkpointer),
        }

        tool_node = ToolNode(self.tools)
        graph = StateGraph(AgentState)
        graph.add_node("assistance", self._assistance_node)
        graph.add_node("tools", tool_node)
        graph.set_entry_point("assistance")
        graph.add_conditional_edges("assistance", self._route, {"tools": "tools", END: END})
        graph.add_edge("tools", "assistance")
        
        self.workflow = graph.compile(checkpointer=self.checkpointer)
        logger.info("Brain listo con ruteo inteligente y subgrafos.")

    @staticmethod
    def _route(state: AgentState) -> str:
        last = state["messages"][-1]
        return "tools" if getattr(last, "tool_calls", None) else END

    def _get_intent(self, input_text: str) -> str:
        prompt = route_prompt().format(input_text=input_text)
        
        response = self._llm_base.invoke([HumanMessage(content=prompt)])
        return response.content.strip().lower()

    def _assistance_node(self, state: AgentState) -> dict:
        customer_id = state["metadata"].get("customer_id", "desconocido")
        system = SystemMessage(content=agent_facturacion().format(customer_id=customer_id))
        response = self.model.invoke([system, *state["messages"]])
        return {"messages": [response]}

    def run(self, input_text: str, customer_id: str):
        if not self.workflow:
            raise RuntimeError("Brain no inicializado; llamá a brain() primero.")

        config_billing   = {"configurable": {"thread_id": f"factura-{customer_id}"}}
        config_retention = {"configurable": {"thread_id": f"retention-{customer_id}"}}

        billing_state   = self.subgrafos["billing"].get_state(config_billing)
        retention_state = self.subgrafos["retention"].get_state(config_retention)

        PASOS_RETENTION_CERRADOS = {"no_elegible", "sin_ofertas", "retencion_aplicada", "error_aplicar"}
        PASOS_BILLING_CERRADOS   = {"ir_a_promise", "ir_a_retention"}
        tiene_billing_activo   = bool(
            billing_state
            and billing_state.values.get("messages")
            and billing_state.values.get("paso_actual") not in PASOS_BILLING_CERRADOS
        )
        tiene_retention_activo = bool(
            retention_state
            and retention_state.values.get("messages")
            and retention_state.values.get("paso_actual") not in PASOS_RETENTION_CERRADOS
        )

        if tiene_retention_activo:
            intent_check = self._get_intent(input_text)
            if intent_check == "billing":
                intent = "billing"
                logger.info(f"[BRAIN] Retention activa pero cliente cambió a billing para cliente {customer_id}")
            else:
                intent = "retention"
                logger.info(f"[BRAIN] Retention activa para cliente {customer_id}, manteniendo flujo (intent_check={intent_check})")
        elif tiene_billing_activo:
            intent = "billing"
            logger.info(f"[BRAIN] Conversación de billing activa para cliente {customer_id}, ruteando directo")
        else:
            intent = self._get_intent(input_text)
            logger.info(f"[BRAIN] Intención detectada: {intent}")

        if intent == "retention":
            logger.info(f"[BRAIN] Derivando a subgrafo retention para cliente {customer_id}")
            return self.subgrafos["retention"].invoke(
                {
                    "messages":    [HumanMessage(content=input_text)],
                    "customer_id": int(customer_id),
                },
                config=config_retention,
            )

        if intent == "billing" and "billing" in self.subgrafos:
            logger.info(f"[BRAIN] Derivando a subgrafo billing para cliente {customer_id}")

            result = self.subgrafos["billing"].invoke(
                {
                    "messages":    [HumanMessage(content=input_text)],
                    "customer_id": str(customer_id),
                },
                config=config_billing,
            )

            if result.get("paso_actual") == "ir_a_promise":
                logger.info(f"[BRAIN] Saltando a promise flow para cliente {customer_id}")
                config_promise = {"configurable": {"thread_id": f"promise-{customer_id}"}}
                return self.subgrafos["promise"].invoke(
                    {
                        "messages":    result.get("messages", [HumanMessage(content=input_text)]),
                        "customer_id": str(customer_id),
                    },
                    config=config_promise,
                )

            if result.get("paso_actual") == "ir_a_retention":
                logger.info(f"[BRAIN] Saltando a retention flow desde billing para cliente {customer_id}")
                return self.subgrafos["retention"].invoke(
                    {
                        "messages":    result.get("messages", [HumanMessage(content=input_text)]),
                        "customer_id": int(customer_id),
                    },
                    config=config_retention,
                )

            return result

        logger.info(f"[BRAIN] Derivando a flujo general para cliente {customer_id}")
        config_general = {"configurable": {"thread_id": str(customer_id)}}
        
        memory = get_memory(customer_id)
        initial_state: AgentState = {
            "messages": ([*memory, HumanMessage(content=input_text)] if memory else [HumanMessage(content=input_text)]),
            "metadata": {"source": "kafka", "customer_id": str(customer_id)},
            "dialog": {},
        }

        result_state = self.workflow.invoke(initial_state, config=config_general)

        try:
            save_conversation(str(customer_id))
        except Exception as e:
            logger.warning("No se pudo guardar la conversación: %s", e)

        return result_state
