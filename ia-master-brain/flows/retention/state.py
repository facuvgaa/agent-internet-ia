from typing import Annotated, TypedDict, Optional
from langchain_core.messages import BaseMessage
from langgraph.graph import add_messages


class RetentionState(TypedDict):

    customer_id: int

    cliente: Optional[dict]
    
    servicios: Optional[list]
    
    eligibility: Optional[dict]
    
    ofertas_preview: Optional[list]
    
    paso_actual: str
    
    messages: Annotated[list[BaseMessage], add_messages]