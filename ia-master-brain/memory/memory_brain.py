import dotenv
import logging
from dotenv import load_dotenv
from langgraph.checkpoint.memory import MemorySaver
from langgraph.checkpoint.postgres import PostgresSaver

import os


load_dotenv()


logger = logging.getLogger(__name__)

_CONVERSATION_DB = os.getenv(
    "CONVERSATION_DB", 
    "postgresql://admin-llm:admin-llm@localhost:5433/conversation-db"
    )

_checkpointer = None


def get_checkpointer():

    """Devuelve el checkpointer para compilar el grafo (Postgres o memoria)."""
    global _checkpointer

    if _checkpointer is not None:
        return _checkpointer
    
    if _CONVERSATION_DB and _CONVERSATION_DB.strip():
        try:
            _checkpointer = PostgresSaver.from_conn_string(_CONVERSATION_DB)
            _checkpointer.setup()
            logger.info("Checkpointer Postgres (conversaciones) listo")
        except Exception as e:
            logger.warning("Postgres no disponible, usando memoria: %s", e)
            _checkpointer = MemorySaver()
    else:
        _checkpointer = MemorySaver()
    
    return _checkpointer

def get_memory(customer_id: str, last_n: int = 5):
    """
    Carga las últimas `last_n` conversaciones (mensajes) para este customer_id.
    Llamar apenas el usuario entra para tener contexto en memoria.
    """
    try:
        cp = get_checkpointer()
        config = {"configurable": {"thread_id": str(customer_id)}}
        t = cp.get_tuple(config)
        if t is None:
            return []
        messages = t.checkpoint.get("channel_values", {}).get("messages", [])
        return list(messages[-last_n:]) if messages else []
    except Exception as e:
        logger.warning("No se pudieron cargar conversaciones para %s: %s", customer_id, e)
        return []


def save_memory(messages, customer_id: str, checkpointer=None):
    """Reservado: guardar/recortar cuando llegue a 10 (siguiente paso)."""

    cp = get_checkpointer()