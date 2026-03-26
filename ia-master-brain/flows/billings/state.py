from typing import Annotated, TypedDict, List, Optional
from langchain_core.messages import BaseMessage
from langgraph.graph import add_messages


class BillingEstate(TypedDict):
    customer_id: int

    cliente: Optional[dict]
    facturas: Optional[list]
    ticket_id: Optional[str]

    paso_actual: Optional[str]


    messages: Annotated[list[BaseMessage], add_messages]

