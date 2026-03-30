import logging
import os
from dotenv import load_dotenv
from langgraph.checkpoint.memory import MemorySaver
from langgraph.checkpoint.redis import RedisSaver
from langchain_core.messages import AIMessage, HumanMessage, BaseMessage
from memory.save_to_db import conexion_to_db
from memory.query import recover_conver, save_conver

load_dotenv()

logger = logging.getLogger(__name__)

_MEMORY_REDIS_URL = os.getenv("MEMORY_REDIS_URL", "redis://localhost:6379")
_CONVERSATION_DB  = os.getenv(
    "CONVERSATION_DB",
    "postgresql://admin-llm:admin-llm@localhost:5433/conversation-db"
)

_checkpointer    = None
_redis_context   = None


def get_checkpointer():
    global _checkpointer, _redis_context

    if _checkpointer is not None:
        return _checkpointer

    if _MEMORY_REDIS_URL and _MEMORY_REDIS_URL.strip():
        try:
            _redis_context = RedisSaver.from_conn_string(_MEMORY_REDIS_URL)
            _checkpointer  = _redis_context.__enter__()
            _checkpointer.setup()
            logger.info("Checkpointer Redis listo en %s", _MEMORY_REDIS_URL)
        except Exception as e:
            logger.warning("Redis no disponible, usando MemorySaver: %s", e)
            _checkpointer = MemorySaver()
    else:
        _checkpointer = MemorySaver()

    return _checkpointer


def close_checkpointer():
    global _redis_context
    if _redis_context is not None:
        try:
            _redis_context.__exit__(None, None, None)
            logger.info("Checkpointer Redis cerrado correctamente.")
        except Exception as e:
            logger.warning("Error cerrando checkpointer: %s", e)


def get_full_conversation(customer_id: str) -> list:
    cp = get_checkpointer()
    config = {"configurable": {"thread_id": str(customer_id)}}
    try:
        t = cp.get_tuple(config)
        if t is None:
            return []
        return t.checkpoint.get("channel_values", {}).get("messages", []) or []
    except Exception as e:
        logger.warning("Error leyendo conversación de Redis: %s", e)
        return []


def get_memory(customer_id: str, last_n: int = 5) -> list:
    try:
        messages = get_full_conversation(customer_id)
        if messages:
            return list(messages[-last_n:])

        history = recover_consersation(customer_id, last_n=last_n)
        return history or []
    except Exception as e:
        logger.warning(
            "No se pudieron cargar conversaciones para %s: %s",
            customer_id, e
        )
        return []


def _row_to_message(rol: str | None, contenido: str | None) -> BaseMessage:
    r = (rol or "").lower().strip()
    c = contenido or ""
    if r in ("human", "user", "humanmessage"):
        return HumanMessage(content=c)
    return AIMessage(content=c)


def recover_consersation(customer_id: str, last_n: int = 5) -> list[BaseMessage]:
    try:
        conn = conexion_to_db()
        cursor = conn.cursor()
        cursor.execute(recover_conver(), (str(customer_id), int(last_n)))
        conver = cursor.fetchall()
        cursor.close()
        conn.close()
        if not conver:
            return []
        messages_desc = [_row_to_message(row[2], row[3]) for row in conver]
        return list(reversed(messages_desc))
    except Exception as e:
        logger.warning("Error recuperando conversación de Postgres: %s", e)
        return []


def save_conversation(customer_id: str) -> int:
    mensajes = get_full_conversation(customer_id)
    logger.info(
        "Guardando %d mensajes de %s en Postgres",
        len(mensajes), customer_id
    )
    if not mensajes:
        return 0

    try:
        conn = conexion_to_db()
        with conn.cursor() as cur:
            for orden, m in enumerate(mensajes):
                rol      = getattr(m, "type", None) or m.__class__.__name__.lower()
                contenido = getattr(m, "content", "")
                cur.execute(save_conver(), (customer_id, orden, rol, contenido))
        conn.commit()
        conn.close()
        return len(mensajes)
    except Exception as e:
        logger.warning("Error guardando conversación en Postgres: %s", e)
        return 0