import os
import re
import json
import logging
import traceback
from dotenv import load_dotenv
from confluent_kafka import Consumer, Producer
from langchain_core.messages import HumanMessage
from connection_llm.llm_conecction import get_bedrock_model_master as llm_master
from agents.llm_brain import LlmBrain

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

load_dotenv()

# --- INICIALIZACIÓN DE MODELOS ---
try:
    chat_haikiu = llm_master()
    logger.info("Connected to AWS Bedrock (Haiku) for Triage")
except Exception as e:
    logger.error(f"Error connecting to Haiku: {e}")

brain = None
try:
    _b = LlmBrain()
    _b.brain() 
    brain = _b
    logger.info("Brain system ready and compiled")
except Exception as e:
    logger.error(f"Error initializing Brain: {e}")

# --- CONFIGURACIÓN KAFKA ---
conf = {
    'bootstrap.servers': 'localhost:9092',
    'group.id': 'agente-primario-group',
    'auto.offset.reset': 'earliest',
    'enable.auto.commit': False,
}

producer_conf = {'bootstrap.servers': 'localhost:9092'}
producer = Producer(producer_conf)
RESPUESTAS_TOPIC = 'respuestas.agente'

consumer = Consumer(conf)
consumer.subscribe(['consultas.usuario'])

# --- FUNCIONES DE SOPORTE ---
def _last_ai_content(messages):
    from langchain_core.messages import AIMessage
    for m in reversed(messages):
        if isinstance(m, AIMessage) and m.content:
            return m.content
    return ""

def _clean_response(text: str) -> str:
    if not text: return ""
    text = re.sub(r"<(function_calls|invoke)>.*?</\1>", "", text, flags=re.DOTALL | re.IGNORECASE)
    return re.sub(r"\n{3,}", "\n\n", text).strip()

def triage_message(message):
    """Clasifica el mensaje inicial usando Haiku."""
    prompt = f"""
    Eres un asistente de triaje. Analiza el mensaje y clasifícalo como 'CONSULTA' o 'RECLAMO'.
    Si el tono es de queja o pide datos específicos de facturas/servicios, usa 'RECLAMO'.

    Mensaje: {message}
    Responde SOLO con la palabra 'RECLAMO' o 'CONSULTA'.
    """
    try:
        response = chat_haikiu.invoke([HumanMessage(content=prompt)])
        return response.content.strip().upper()
    except Exception as e:
        logger.error(f"Triage failed: {e}")
        return "ERROR"


def is_conversation_finished(content: str, clasificacion: str) -> bool:
    """
    Punto único para decidir si esta interacción cierra la conversación.
    Implementá acá tu lógica de negocio (palabras clave, estado de ticket, etc.).
    Por ahora siempre devuelve False para no cortar el chat.
    """
    return False


# --- LOOP PRINCIPAL ---
logger.info("Consumer started and polling...")


conversation_route: dict[str, str] = {}

try:
    while True:
        msg = consumer.poll(1.0)
        if msg is None: continue
        if msg.error(): continue

        try:
            raw_data = msg.value().decode('utf-8')
            data = json.loads(raw_data)

            content = data.get("contenido", data.get("content", ""))
            # Buscamos el customer_id, por defecto 1 para no romper la tool
            customer_id = data.get("customer_id") or data.get("id_cliente") or 1
            customer_id = str(customer_id)  # brain.run espera str

            logger.info(f"Mensaje de Kafka: '{content}' | ID: {customer_id}")

            # 1. Ver ruteo actual de esta conversación
            route = conversation_route.get(customer_id)

            # 2. Si ya está ruteada al brain, saltamos el master
            if route == "BRAIN":
                clasificacion = "RECLAMO"  # ya fue clasificado antes

                if brain:
                    try:
                        estado = brain.run(content, customer_id)
                        raw_res = _last_ai_content(estado.get("messages", []))
                        respuesta_final = _clean_response(raw_res)
                        if not respuesta_final:
                            logger.warning(
                                "Brain devolvió respuesta vacía (mensajes=%s)",
                                len(estado.get("messages", [])),
                            )
                            respuesta_final = "No pude generar una respuesta. Revisá los logs del agente."

                        payload = json.dumps(
                            {
                                "respuesta": respuesta_final,
                                "customer_id": customer_id,
                                "contenido_original": content,
                                "clasificacion": clasificacion,
                            },
                            ensure_ascii=False,
                        )
                        producer.produce(RESPUESTAS_TOPIC, value=payload.encode("utf-8"))
                        producer.flush()
                        logger.info(f"[OK] Respuesta enviada (brain directo) para ID {customer_id}")

                        if is_conversation_finished(content, clasificacion):
                            # Más adelante: save_conversation(customer_id) y limpieza de memoria corta
                            conversation_route.pop(customer_id, None)

                        consumer.commit(message=msg)
                    except Exception as e:
                        logger.error(f"Error procesando en Brain (ruta directa): {e}\n{traceback.format_exc()}")
                else:
                    logger.error("Brain no inicializado: no se puede procesar el mensaje (ruta directa)")

                continue  # pasamos al siguiente mensaje del loop

            # 3. Si aún no está ruteado al brain -> usar master (triaje)
            clasificacion = triage_message(content)
            logger.info(f"Clasificación: {clasificacion}")

            respuesta_enviada = False

            if clasificacion == "RECLAMO" and brain:
                # Primera vez que lo mandamos al brain: marcamos ruteo
                conversation_route[customer_id] = "BRAIN"

                try:
                    estado = brain.run(content, customer_id)
                    raw_res = _last_ai_content(estado.get("messages", []))
                    respuesta_final = _clean_response(raw_res)
                    if not respuesta_final:
                        logger.warning(
                            "Brain devolvió respuesta vacía (mensajes=%s)",
                            len(estado.get("messages", [])),
                        )
                        respuesta_final = "No pude generar una respuesta. Revisá los logs del agente."

                    payload = json.dumps(
                        {
                            "respuesta": respuesta_final,
                            "customer_id": customer_id,
                            "contenido_original": content,
                            "clasificacion": clasificacion,
                        },
                        ensure_ascii=False,
                    )
                    producer.produce(RESPUESTAS_TOPIC, value=payload.encode("utf-8"))
                    producer.flush()
                    respuesta_enviada = True
                    logger.info(f"[OK] Respuesta enviada (master→brain) para ID {customer_id}")

                except Exception as e:
                    logger.error(f"Error procesando en Brain (tras master): {e}\n{traceback.format_exc()}")
                    payload = json.dumps(
                        {
                            "respuesta": f"Error interno del agente: {e}",
                            "customer_id": customer_id,
                            "contenido_original": content,
                            "clasificacion": clasificacion,
                            "error": True,
                        },
                        ensure_ascii=False,
                    )
                    producer.produce(RESPUESTAS_TOPIC, value=payload.encode("utf-8"))
                    producer.flush()
                    respuesta_enviada = True

            else:
                # CONSULTA u otros casos: por ahora solo logueamos; acá podés
                # mantener el flujo actual de master o derivar a otro agente.
                logger.info(
                    "Mensaje clasificado como '%s' para ID %s. Mantener flujo de master o implementar otra ruta.",
                    clasificacion,
                    customer_id,
                )

            if respuesta_enviada:
                consumer.commit(message=msg)

        except Exception as e:
            logger.error(f"Error en loop de mensaje: {e}")

except KeyboardInterrupt:
    logger.info("Stopping...")
finally:
    consumer.close()




