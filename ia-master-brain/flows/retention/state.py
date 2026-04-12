from typing import Annotated, TypedDict, Optional
from langchain_core.messages import BaseMessage
from langgraph.graph import add_messages


class RetentionState(TypedDict):

    customer_id: int

    cliente: Optional[dict]
    service_id: Optional[int]

    eligibility: Optional[dict]
    retention_tiers: list

    selected_level: Optional[int]
    preview: Optional[dict]

    application_result: Optional[str]

    paso_actual: str

    messages: Annotated[list[BaseMessage], add_messages]