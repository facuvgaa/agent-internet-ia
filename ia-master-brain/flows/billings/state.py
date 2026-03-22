from typing import TypedDict, Optional, Annotated, List
from langchain_core.messages import BaseMessage
from langgraph.graph.message import add_messages


class BillingState(TypedDict):
    customerId: int

    cliente:        Optional[dict]   
    facturas:       Optional[list]   
    factura_id:     Optional[int]    
    ticket_id:      Optional[str]    
    promesa_pago:   Optional[dict]   

    paso_actual:    Optional[str]    
    
    messages: Annotated[List[BaseMessage], add_messages]
