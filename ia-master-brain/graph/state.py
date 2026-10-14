from __future__ import annotations

from typing import Annotated, Optional, TypedDict


def _merge_messages(existing: list[dict], new: list[dict]) -> list[dict]:
    """
    Reducer acumulador para la lista de mensajes.
    Equivalente funcional a langchain_core.messages.add_messages
    pero operando sobre dicts nativos del formato Bedrock Converse.

    Cada mensaje tiene la forma:
        {"role": "user"|"assistant", "content": [block, ...]}
    donde block puede ser:
        - {"text": "..."}
        - {"toolUse": {"toolUseId": "...", "name": "...", "input": {...}}}
        - {"toolResult": {"toolUseId": "...", "content": [...], "status": "success"|"error"}}
    """
    if existing is None:
        return new or []
    return existing + (new or [])


class AgentState(TypedDict):
    """Estado unificado del grafo. Sin dependencias de LangChain."""

    # ── Identidad ──────────────────────────────────────────────────────────
    customer_id: str  # siempre string (Kafka envía strings)

    # ── Routing ────────────────────────────────────────────────────────────
    domain: str      # "billing" | "retention" | "promise" | "general" | ""
    paso_actual: str  # "en_progreso" | "respondido" | "retencion_aplicada" | etc.

    # ── Historial de mensajes (formato nativo Bedrock Converse) ────────────
    messages: Annotated[list[dict], _merge_messages]

    # ── Datos de dominio cacheados ─────────────────────────────────────────
    cliente: Optional[dict]           # get_customer_info
    facturas: Optional[list]          # billing_info
    servicios: Optional[list]         # get_customer_service
    eligibility: Optional[dict]       # get_retention_eligibility
    ofertas_preview: Optional[list]   # get_retention_preview

    # ── Salida ─────────────────────────────────────────────────────────────
    ticket_id: Optional[str]
    ultima_respuesta: str             # texto final para extraer en consumer
