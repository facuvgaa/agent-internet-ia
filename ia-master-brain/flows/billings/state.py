from typing import Annotated, TypedDict, List, Optional
from langchain_core.messages import BaseMessage
from langgraph.graph import add_messages


class BillingEstate(TypedDict):
    customer_id: int

    cliente: Optional[dict]
    facturas: list
    ticket_id: Optional[str]
    servicios: Optional[list] 

    paso_actual: str


    messages: Annotated[list[BaseMessage], add_messages]

