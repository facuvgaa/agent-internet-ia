import operator
from typing import Annotated, TypedDict
from langgraph.graph import StateGraph, END, state
from langchain_aws import ChatBedrock
from langchain_core.messages import HumanMessage, BaseMessage
import logging
from dotenv import load_dotenv
import os

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

load_dotenv()

class AgentEstate(TypedDict):
    messages: Annotated(list[BaseMessage], operator.add)
    metadata:dict

class LlmBrain():
    def brain(self):
        try:
            self.model = ChatBedrock(
                model_llm = os.getenv("AWS_SECOND_LLM", "us.anthropic.claude-3-5-sonnet-20240620-v1:0"),
                region_name = os.getenv("AWS_REGION", "us-east-1")
            )
            logger.info("conect to aws bedrock(claude in connect)")

            self.workflow = self._build_graph()

        except Exception as e:
            logger.error(f"error to connect brain {e}")

    def __call_brain(self, state: AgentEstate):
        logger.info("technical claim")
        response = self.model.invoke(state["messages"])
        return {"messages": [response]}

    def __build_graph(self):

        graph = StateGraph(AgentEstate)

        graph.add_node("tecnico_node", self.__call_brain)

        graph.set_entry_point("tecnico_node")
        graph.add_edge("tecnico_node", END)

        return graph.compile()

    def run(self, input_text: str):

        initial_state ={
            "messages": [HumanMessage(content=input_text)],
            "metadata": {"source": "kafka"}
        }

        return self.workflow.invoke(initial_state)
        

