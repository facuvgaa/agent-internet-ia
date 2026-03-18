import dotenv
import logging
from dotenv import load_dotenv
from langgraph.checkpoint.memory import MemorySaver
from langgraph.checkpoint.redis import RedisSaver
from langchain_core.messages import AIMessage, HumanMessage, BaseMessage
from memory.save_to_db import conexion_to_db
from memory.query import recover_conver, save_conver
import os


load_dotenv()


logger = logging.getLogger(__name__)


_MEMORY_REDIS_URL = os.getenv("MEMORY_REDIS_URL", "redis://localhost:6379")
_CONVERSATION_DB = os.getenv(
    "CONVERSATION_DB", 
    "postgresql://admin-llm:admin-llm@localhost:5433/conversation-db"
    )

_checkpointer = None


def get_checkpointer():
    global _checkpointer

    if _checkpointer is not None:
        return _checkpointer
    
    if _MEMORY_REDIS_URL and _MEMORY_REDIS_URL.strip():
        try:
            _checkpointer = RedisSaver.from_conn_string(_MEMORY_REDIS_URL)
            _checkpointer.setup()
            logger.info("Checkpointer Redis (conversaciones) listo")
        except Exception as e:
            logger.warning("Redis no disponible, usando memoria: %s", e)
            _checkpointer = MemorySaver()
    else:
        _checkpointer = MemorySaver()
    
    return _checkpointer

def get_memory(customer_id: str, last_n: int = 5):
    try:
        messages = get_full_conversation(customer_id)
        if messages:
            return list(messages[-last_n:])

        history = recover_consersation(customer_id, last_n=last_n)
        return history or []
    except Exception as e:
        logger.warning("No se pudieron cargar conversaciones para %s: %s", customer_id, e)
        return []

def get_full_conversation(customer_id: str):
    cp = get_checkpointer()
    config = {"configurable": {"thread_id": str(customer_id)}}
    t = cp.get_tuple(config)
    if t is None:
        return []
    return t.checkpoint.get("channel_values", {}).get("messages", []) or []


def _row_to_message(rol: str | None, contenido: str | None) -> BaseMessage:
    r = (rol or "").lower().strip()
    c = contenido or ""
    if r in ("human", "user", "humamessage"):
        return HumanMessage(content=c)
    if r in ("ai", "assistant", "aimessage"):
        return AIMessage(content=c)
    return AIMessage(content=c)


def recover_consersation(customer_id: str, last_n: int = 5) -> list[BaseMessage]:
    conn = conexion_to_db()

    cursor = conn.cursor()

    cursor.execute(recover_conver(), (str(customer_id), int(last_n)))

    conver = cursor.fetchall()

    if not conver:
        return []

    messages_desc = [_row_to_message(row[2], row[3]) for row in conver]
    return list(reversed(messages_desc))
            

def save_conversation(customer_id: str):
    mensajes = get_full_conversation(customer_id)
    logger.info(
        "Guardando %d mensajes de la conversación de %s en Postgres",
        len(mensajes),
        customer_id,
    )
    if not mensajes:
        return 0
    conn = conexion_to_db()
    try:
        with conn.cursor() as cur:
            for orden, m in enumerate(mensajes):
                rol = getattr(m, "type", None) or m.__class__.__name__.lower()
                contenido = getattr(m, "content", "")
                cur.execute(
                    save_conver(),
                    (customer_id, orden, rol, contenido),
                )
    finally:
        conn.close()
    return len(mensajes)
