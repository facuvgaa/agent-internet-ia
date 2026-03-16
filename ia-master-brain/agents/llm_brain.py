from __future__ import annotations
import logging
import operator
from typing import Annotated, TypedDict
from dotenv import load_dotenv
from langchain_core.messages import HumanMessage, BaseMessage, SystemMessage, ToolMessage
from langgraph.graph import StateGraph
from langgraph.prebuilt import tools_condition, ToolNode
from connection_llm.llm_conecction import get_bedrock_model_brain as llm_brain
from tools.tools import get_customer_info, get_customer_service, create_ticket
from context_llm.contexts import agent_facturacion
from langgraph.checkpoint.memory import InMemorySaver


checkpointer = InMemorySaver()
builder = StateGraph(...)

graph = builder.compile(checkpointer=checkpointer)

tools = [get_customer_info, get_customer_service, create_ticket]

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)
load_dotenv()


class AgentEstate(TypedDict):
    messages: Annotated[list[BaseMessage], operator.add]
    metadata: dict


class LlmBrain:
    def __init__(self):
        self.model = None
        self.workflow = None

    def brain(self):
        """Inicializa el modelo y construye el grafo."""
        try:
            base = llm_brain()
            self.model = base.bind_tools(tools)
            logger.info("Conectado a AWS Bedrock (Claude) con herramientas vinculadas")
            self.workflow = self.__build_graph()
        except Exception as e:
            logger.error(f"Error al inicializar el cerebro: {e}")
            raise

    def __call_brain(self, state: AgentEstate):
        tengo_datos_de_tool = any(isinstance(m, ToolMessage) for m in state["messages"])
        if not tengo_datos_de_tool:
            model_con_fuerza = self.model.bind_tools(tools, tool_choice="get_customer_info")
            response = model_con_fuerza.invoke(state["messages"])
        else:
            response = self.model.invoke(state["messages"])
        return {"messages": [response]}

    def __build_graph(self):
        tools_node = ToolNode(tools)
        graph = StateGraph(AgentEstate)
        graph.add_node("tecnico_node", self.__call_brain)
        graph.add_node("tools", tools_node)
        graph.set_entry_point("tecnico_node")
        graph.add_conditional_edges("tecnico_node", tools_condition)
        graph.add_edge("tools", "tecnico_node")
        return graph.compile()

    def run(self, input_text: str, customer_id: str):
        if not self.workflow:
            raise RuntimeError("El cerebro no ha sido inicializado. Llamá a brain() primero.")
        contexto_usuario = f"[CONTEXTO: customer_id={customer_id}]\nConsulta del usuario: {input_text}"
        initial_state = {
            "messages": [
                SystemMessage(content=agent_facturacion()),
                HumanMessage(content=contexto_usuario),
            ],
            "metadata": {"source": "kafka"},
        }
        return self.workflow.invoke(initial_state)

