import dotenv
import logging
from dotenv import load_dotenv
from langgraph.checkpoint.memory import MemorySaver
from langgraph.checkpoint.redis import RedisSaver
from memory_brain import conexion_to_db
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

    """
    Devuelve el checkpointer para compilar el grafo (Redis o memoria).
    Este checkpointer es el que LangGraph usa para guardar el estado vivo por thread_id.
    """

    global _checkpointer

    if _checkpointer is not None:
        return _checkpointer
    
    if _MEMORY_REDIS_URL and _MEMORY_REDIS_URL.strip():
        try:
            _checkpointer = RedisSaver.from_conn_string(_CONVERSATION_DB)
            _checkpointer.setup()
            logger.info("Checkpointer Redis (conversaciones) listo")
        except Exception as e:
            logger.warning("Redis no disponible, usando memoria: %s", e)
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
        messages = get_full_conversation(customer_id)
        return list(messages[-last_n:]) if messages else []
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


def save_conversation(customer_id: str):
    """
    Lee toda la conversación viva de este customer_id (desde Redis)
    y la guarda en la base histórica (conversation-db), tabla conversation_history.
    """
    mensajes = get_full_conversation(customer_id)
    logger.info(
        "Guardando %d mensajes de la conversación de %s en Postgres",
        len(mensajes),
        customer_id,
    )
    if not mensajes:
        return 0
    # Conectar a la base de conversaciones
    conn = conexion_to_db()
    try:
        with conn.cursor() as cur:
            for orden, m in enumerate(mensajes):
                rol = getattr(m, "type", None) or m.__class__.__name__.lower()
                contenido = getattr(m, "content", "")
                cur.execute(
                    """
                    INSERT INTO conversation_history (customer_id, orden, rol, contenido)
                    VALUES (%s, %s, %s, %s)
                    """,
                    (customer_id, orden, rol, contenido),
                )
    finally:
        conn.close()
    return len(mensajes)
