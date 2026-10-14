import json
import logging
import os
import re
import traceback

import redis
from confluent_kafka import Consumer, Producer
from dotenv import load_dotenv

from graph.agent_graph import build_agent_graph
from llm.bedrock_client import BedrockClient
from mcp_client.client import initialize_mcp
from memory.memory_brain import (
    close_checkpointer,
    get_checkpointer,
    get_memory,
    save_conversation,
)

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)
load_dotenv()

# ── Inicialización del cliente MCP ────────────────────────────────────────────
MCP_SERVER_URL = os.getenv("MCP_SERVER_URL", "http://localhost:8080/mcp/message")
try:
    initialize_mcp(MCP_SERVER_URL)
    logger.info("[MCP] client inicializado en %s", MCP_SERVER_URL)
except Exception as e:
    logger.error("[MCP] Error inicializando client: %s", e)

# ── Inicialización del LLM Bedrock ────────────────────────────────────────────
try:
    bedrock = BedrockClient()
    logger.info("BedrockClient listo")
except Exception as e:
    logger.error("Error inicializando BedrockClient: %s", e)
    bedrock = None

# ── Inicialización del grafo ──────────────────────────────────────────────────
brain_graph = None
try:
    checkpointer = get_checkpointer()
    brain_graph = build_agent_graph(checkpointer)
    logger.info("[AgentGraph compilado]")
except Exception as e:
    logger.error("Error inicializando AgentGraph: %s", e)

# ── Redis para ruteo (db=1) ───────────────────────────────────────────────────
# db=0 → memoria del brain (checkpointer LangGraph)
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


# ── Kafka ─────────────────────────────────────────────────────────────────────
_KAFKA_BOOTSTRAP = os.getenv("KAFKA_BOOTSTRAP", "localhost:9092")
consumer = Consumer({
    "bootstrap.servers": _KAFKA_BOOTSTRAP,
    "group.id": "agente-primario-group",
    "auto.offset.reset": "earliest",
    "enable.auto.commit": False,
})
producer = Producer({"bootstrap.servers": _KAFKA_BOOTSTRAP})
RESPUESTAS_TOPIC = "respuestas.agente"
consumer.subscribe(["consultas.usuario"])

# Conjunto de pasos que indican conversación cerrada
PASOS_CERRADOS = {
    "cerrado",
    "retencion_aplicada",
    "promesa_procesada",
    "reclamo_procesado",
}


# ── Helpers ───────────────────────────────────────────────────────────────────

def _clean_response(text: str) -> str:
    if not text:
        return ""
    text = re.sub(
        r"<(function_calls|invoke)>.*?</\1>", "", text, flags=re.DOTALL | re.IGNORECASE
    )
    return re.sub(r"\n{3,}", "\n\n", text).strip()


def send_response(
    respuesta: str, customer_id: str, content: str, clasificacion: str
) -> None:
    payload = json.dumps(
        {
            "respuesta": respuesta,
            "customer_id": customer_id,
            "contenido_original": content,
            "clasificacion": clasificacion,
        },
        ensure_ascii=False,
    )
    producer.produce(RESPUESTAS_TOPIC, value=payload.encode("utf-8"))
    producer.flush()


def triage(content: str) -> str:
    """Corre UNA SOLA VEZ por conversación. Clasifica en RECLAMO o CONSULTA."""
    if bedrock is None:
        return "ERROR"
    try:
        prompt = f"""Clasificá el mensaje como RECLAMO o CONSULTA.
RECLAMO: quejas, facturas, servicios caídos, pedir descuentos o si dice "tengo un problema", "quiero averiguar", se clasifica como reclamo.
CONSULTA: preguntas generales sobre planes, cobertura, horarios.
Respondé SOLO con una palabra, clasifica bien lee el mensaje.
Mensaje: {content}"""
        result = bedrock.invoke_simple(prompt)
        return result.strip().upper()
    except Exception as e:
        logger.error("Triage failed: %s", e)
        return "ERROR"


def responder_consulta(content: str) -> str:
    """Responde consultas generales sin datos del cliente."""
    if bedrock is None:
        return "No pude procesar tu consulta."
    try:
        prompt = f"""Sos un asistente de telecomunicaciones.
Respondé esta consulta general de forma clara y breve.
No tenés acceso a datos del cliente, solo información general.
Consulta: {content}"""
        return bedrock.invoke_simple(prompt).strip()
    except Exception as e:
        logger.error("Consulta simple failed: %s", e)
        return "No pude procesar tu consulta. Intentá de nuevo."


def procesar_brain(content: str, customer_id: str) -> tuple[str, bool]:
    """
    Ejecuta el grafo con el mensaje del cliente.
    Retorna (respuesta_texto, conversacion_cerrada).
    """
    if brain_graph is None:
        raise RuntimeError("AgentGraph no inicializado")

    config = {"configurable": {"thread_id": str(customer_id)}}

    # Recuperar memoria previa (Postgres fallback si Redis no tiene estado)
    memory = get_memory(customer_id)

    # Construir el mensaje de usuario en formato Bedrock Converse
    user_message = {"role": "user", "content": [{"text": content}]}
    initial_messages = (memory or []) + [user_message]

    result_state = brain_graph.invoke(
        {
            "messages": initial_messages,
            "customer_id": str(customer_id),
            "domain": "",
            "paso_actual": "",
            "ultima_respuesta": "",
        },
        config=config,
    )

    respuesta = _clean_response(result_state.get("ultima_respuesta", ""))

    # Fallback: extraer último mensaje de assistant del historial
    if not respuesta:
        for m in reversed(result_state.get("messages", [])):
            if m.get("role") == "assistant":
                for block in m.get("content", []):
                    if "text" in block and block["text"]:
                        respuesta = _clean_response(block["text"])
                        break
            if respuesta:
                break

    if not respuesta:
        respuesta = "No pude generar una respuesta. Revisá los logs."

    cerrada = result_state.get("paso_actual") in PASOS_CERRADOS

    try:
        save_conversation(str(customer_id))
    except Exception as e:
        logger.warning("No se pudo guardar la conversación: %s", e)

    return respuesta, cerrada


# ── Loop principal ────────────────────────────────────────────────────────────
logger.info("Consumer started.")

try:
    while True:
        msg = consumer.poll(1.0)
        if msg is None or msg.error():
            continue

        try:
            data = json.loads(msg.value().decode("utf-8"))
            content = data.get("contenido", data.get("content", ""))
            customer_id = str(
                data.get("customer_id") or data.get("id_cliente") or 1
            )

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
                        logger.info(
                            "[ROUTE] conversación cerrada ID %s", customer_id
                        )
                except Exception as e:
                    logger.error(
                        "Error en brain: %s\n%s", e, traceback.format_exc()
                    )

            # ── ya clasificado como CONSULTA ──
            elif route == "CONSULTA":
                respuesta = responder_consulta(content)
                send_response(respuesta, customer_id, content, "CONSULTA")
                logger.info("[CONSULTA] respuesta enviada ID %s", customer_id)

            # ── primera vez → triage ──
            else:
                clasificacion = triage(content)
                logger.info("Triage: %s | ID: %s", clasificacion, customer_id)

                if clasificacion == "RECLAMO" and brain_graph:
                    set_route(customer_id, "BRAIN")
                    try:
                        respuesta, cerrada = procesar_brain(content, customer_id)
                        send_response(
                            respuesta, customer_id, content, clasificacion
                        )
                        if cerrada:
                            delete_route(customer_id)
                    except Exception as e:
                        logger.error("Error brain tras triage: %s", e)
                        send_response(
                            f"Error interno: {e}",
                            customer_id,
                            content,
                            clasificacion,
                        )

                elif clasificacion == "CONSULTA":
                    set_route(customer_id, "CONSULTA")
                    respuesta = responder_consulta(content)
                    send_response(respuesta, customer_id, content, clasificacion)

                else:
                    logger.warning("Clasificación inválida: %s", clasificacion)

            consumer.commit(message=msg)

        except Exception as e:
            logger.error("Error en loop: %s\n%s", e, traceback.format_exc())

except KeyboardInterrupt:
    logger.info("Stopping...")
finally:
    consumer.close()
    close_checkpointer()
