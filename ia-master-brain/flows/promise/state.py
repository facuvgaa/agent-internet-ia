from typing import Annotated, TypedDict, Optional
from langchain_core.messages import BaseMessage
from langgraph.graph import add_messages



class PromiseEstate(TypedDict):

    customer_id: int

    cliente: Optional[dict]
    factura_defeated: list
    puede_prometer: bool

    paso_actual: str

    messages: Annotated[list[BaseMessage], add_messages]
