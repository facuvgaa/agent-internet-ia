import os
import re
import json
import logging
import traceback
import redis
from dotenv import load_dotenv
from confluent_kafka import Consumer, Producer
from langchain_core.messages import HumanMessage
from connection_llm.llm_conecction import get_bedrock_model_master as llm_master
from agents.llm_brain import LlmBrain
from memory.memory_brain import close_checkpointer

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)
load_dotenv()

# --- MODELOS ---
try:
    chat_haiku = llm_master()
    logger.info("Connected to AWS Bedrock (Haiku)")
except Exception as e:
    logger.error(f"Error connecting to Haiku: {e}")

brain = None
try:
    _b = LlmBrain()
    _b.brain()
    brain = _b
    logger.info("Brain ready")
except Exception as e:
    logger.error(f"Error initializing Brain: {e}")

# --- REDIS para ruteo ---
# db=0 → memoria del brain (checkpointer)
# db=1 → ruteo de conversaciones (este consumer)
_MEMORY_REDIS_URL = os.getenv("MEMORY_REDIS_URL", "redis://localhost:6379")
redis_route = redis.Redis.from_url(
    _MEMORY_REDIS_URL, db=1, decode_responses=True
)
ROUTE_TTL = 60 * 60 * 24  # 24 horas en segundos

def get_route(customer_id: str) -> str | None:
    return redis_route.get(f"route:{customer_id}")

def set_route(customer_id: str, route: str) -> None:
    redis_route.setex(f"route:{customer_id}", ROUTE_TTL, route)

def delete_route(customer_id: str) -> None:
    redis_route.delete(f"route:{customer_id}")

# --- KAFKA ---
_KAFKA_BOOTSTRAP = os.getenv("KAFKA_BOOTSTRAP", "localhost:9092")
consumer = Consumer({
    'bootstrap.servers': _KAFKA_BOOTSTRAP,
    'group.id': 'agente-primario-group',
    'auto.offset.reset': 'earliest',
    'enable.auto.commit': False,
})
producer = Producer({'bootstrap.servers': _KAFKA_BOOTSTRAP})
RESPUESTAS_TOPIC = 'respuestas.agente'
consumer.subscribe(['consultas.usuario'])

# --- HELPERS ---
def _coerce_message_content(content) -> str:
    """Bedrock/Converse puede devolver content como str o lista de bloques."""
    if content is None:
        return ""
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        parts: list[str] = []
        for block in content:
            if isinstance(block, str):
                parts.append(block)
            elif isinstance(block, dict):
                t = block.get("text") or block.get("content")
                if isinstance(t, str):
                    parts.append(t)
            else:
                parts.append(str(block))
        return "".join(parts)
    return str(content)


def _last_ai_content(messages) -> str:
    from langchain_core.messages import AIMessage
    for m in reversed(messages):
        if isinstance(m, AIMessage) and m.content:
            return _coerce_message_content(m.content)
    return ""

def _clean_response(text) -> str:
    text = _coerce_message_content(text)
    if not text:
        return ""
    text = re.sub(
        r"<(function_calls|invoke)>.*?</\1>", "",
        text, flags=re.DOTALL | re.IGNORECASE
    )
    return re.sub(r"\n{3,}", "\n\n", text).strip()

def send_response(respuesta: str, customer_id: str,
                  content: str, clasificacion: str) -> None:
    payload = json.dumps({
        "respuesta":          respuesta,
        "customer_id":        customer_id,
        "contenido_original": content,
        "clasificacion":      clasificacion,
    }, ensure_ascii=False)
    producer.produce(RESPUESTAS_TOPIC, value=payload.encode("utf-8"))
    producer.flush()

def triage(content: str) -> str:
    """Corre UNA SOLA VEZ por conversación. Clasifica en RECLAMO o CONSULTA."""
    try:
        response = chat_haiku.invoke([HumanMessage(content=f"""
Clasificá el mensaje como RECLAMO o CONSULTA.
RECLAMO: quejas, facturas, servicios caídos, pedir descuentos o  si dice "tengo un problema", "quiero averiguar", se clasficia como reclamo.
CONSULTA: preguntas generales sobre planes, cobertura, horarios.
Respondé SOLO con una palabra, clasifica bien lee el mensaje.
Mensaje: {content}""")])
        return response.content.strip().upper()
    except Exception as e:
        logger.error("Triage failed: %s", e)
        return "ERROR"

def responder_consulta(content: str) -> str:
    try:
        response = chat_haiku.invoke([HumanMessage(content=f"""
Sos un asistente de telecomunicaciones.
Respondé esta consulta general de forma clara y breve.
No tenés acceso a datos del cliente, solo información general.
Consulta: {content}""")])
        return response.content.strip()
    except Exception as e:
        logger.error("Consulta simple failed: %s", e)
        return "No pude procesar tu consulta. Intentá de nuevo."

def procesar_brain(content: str, customer_id: str) -> tuple[str, bool]:
    estado = brain.run(content, customer_id)
    raw = _last_ai_content(estado.get("messages", []))
    respuesta = _clean_response(raw) or \
        "No pude generar una respuesta. Revisá los logs."
    cerrada = estado.get("paso_actual") == "cerrado"
    return respuesta, cerrada

# --- LOOP PRINCIPAL ---
logger.info("Consumer started.")

try:
    while True:
        msg = consumer.poll(1.0)
        if msg is None or msg.error(): continue

        try:
            data        = json.loads(msg.value().decode('utf-8'))
            content     = data.get("contenido", data.get("content", ""))
            customer_id = str(data.get("customer_id") or data.get("id_cliente") or 1)

            logger.info("Mensaje: '%s' | ID: %s", content, customer_id)

            route = get_route(customer_id)
            logger.info("[ROUTE] customer=%s route=%s", customer_id, route)

            # ── ya clasificado como RECLAMO ──
            if route == "BRAIN":
                try:
                    respuesta, cerrada = procesar_brain(content, customer_id)
                    send_response(respuesta, customer_id, content, "RECLAMO")
                    logger.info("[BRAIN] respuesta enviada ID %s", customer_id)
                    if cerrada:
                        delete_route(customer_id)
                        logger.info("[ROUTE] conversación cerrada ID %s", customer_id)
                except Exception as e:
                    logger.error("Error en brain: %s\n%s", e, traceback.format_exc())

            # ── ya clasificado como CONSULTA ──
            elif route == "CONSULTA":
                respuesta = responder_consulta(content)
                send_response(respuesta, customer_id, content, "CONSULTA")
                logger.info("[CONSULTA] respuesta enviada ID %s", customer_id)

            # ── primera vez → triage ──
            else:
                clasificacion = triage(content)
                logger.info("Triage: %s | ID: %s", clasificacion, customer_id)

                if clasificacion == "RECLAMO" and brain:
                    set_route(customer_id, "BRAIN")
                    try:
                        respuesta, cerrada = procesar_brain(content, customer_id)
                        send_response(respuesta, customer_id, content, clasificacion)
                        if cerrada:
                            delete_route(customer_id)
                    except Exception as e:
                        logger.error("Error brain tras triage: %s", e)
                        send_response(
                            f"Error interno: {e}",
                            customer_id, content, clasificacion
                        )

                elif clasificacion == "CONSULTA":
                    set_route(customer_id, "CONSULTA")
                    respuesta = responder_consulta(content)
                    send_response(respuesta, customer_id, content, clasificacion)

                else:
                    logger.warning("Clasificación inválida: %s", clasificacion)

            consumer.commit(message=msg)

        except Exception as e:
            logger.error("Error en loop: %s", e)

except KeyboardInterrupt:
    logger.info("Stopping...")
finally:
    consumer.close()
    close_checkpointer()



