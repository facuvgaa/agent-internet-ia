import operator
from typing import Annotated, TypedDict
from langgraph.graph import StateGraph, END, state
from langchain_core.messages import HumanMessage, BaseMessage, SystemMessage
import logging
from dotenv import load_dotenv
import os
from connection_llm.llm_conecction import get_bedrock_model_brain as llm_brain
from langgraph.prebuilt import tools_condition, tool_node
from tools import tools
from context_llm.contexts import agent_facturacion


logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

load_dotenv()

class AgentEstate(TypedDict):
    messages: Annotated(list[BaseMessage], operator.add)
    metadata:dict

class LlmBrain():
    def brain(self):
        try:
            self.model = llm_brain().bind_tools(tools)

            logger.info("conect to aws bedrock(claude in connect)")

            self.workflow = self._build_graph()

        except Exception as e:
            logger.error(f"error to connect brain {e}")

    def __call_brain(self, state: AgentEstate):
        logger.info("technical claim")
        response = self.model.invoke(state["messages"])
        return {"messages": [response]}

    def __build_graph(self):

        tool_node = tool_node(tools)

        graph = StateGraph(AgentEstate)

        graph.add_node("tecnico_node", self.__call_brain)
        graph.add_node("tools", tool_node)

        graph.add_conditional_edges(
            "tecnico_node",
            tools_condition,
            path_map={"tools": "tools", "__end__": END},
        )

        graph.set_entry_point("tecnico_node")
        graph.add_edge("tools", "tecnico_node")
        return graph.compile()
        

    def run(self, input_text: str):

        initial_state ={
            "messages": [
                SystemMessage(content=agent_facturacion()),
                HumanMessage(content=input_text)],
            "metadata": {"source": "kafka"}
        }

        return self.workflow.invoke(initial_state)
        

