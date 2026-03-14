import os 
import json
from uu import decode
from dotenv import load_dotenv
from confluent_kafka import Consumer, Producer, KafkaError
from langchain_aws import ChatBedrock
from langchain_core.messages import HumanMessage
import logging
from connection_llm.llm_conecction import get_bedrock_model_master as llm_master

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


load_dotenv()

try:

    chat_haikiu = llm_master()
    logger.info("Conect to AWS Bedrock (Haiku)")
except Exception as e:
    logger.erro(F"error connectin to kaikiu {e}")


conf = {
    'bootstrap.servers': 'localhost:9092',
    'group.id': 'agente-primario-group',
    'auto.offset.reset': 'earliest'
}


consumer = Consumer(conf)

consumer.subscribe(['consultas.usuario'])

logging.info("Consumer started // consumer iniciado")


def process_message(message):
    
    prompt = f"""
    Eres un asistente de triaje técnico. 
    Analiza el siguiente mensaje y clasifícalo como 'CONSULTA' o 'RECLAMO_TECNICO'.
    
    Mensaje: {message}
    
    Responde SOLO con la palabra 'RECLAMO' o 'CONSULTA'.
    """

    try:
        response = chat_haikiu.invoke([HumanMessage(content=prompt)])
        return response.content.strip()
    except Exception as e:
        logger.error(f"erro in the Ai call: {e}")
        return "ERROR"


try:
    while True:
        msg = consumer.poll(1.0)
        if msg is None:continue
        if msg.error(): continue
        try:
            raw_data = msg.value().decode('utf-8')
            data = json.loads(raw_data)

            content = data.get("contenido", data.get("content", ""))

            logging.info(f"Nuevo mensaje recibido: '{content}")

            result = process_message(content)

            logging.info(f"haikiu lo clasifico como {result}")

            if "CLAIM" in result:
                logging.info("[ESCALED] sending to Opus Agent..")
            else:
                logging.info("[INFO] driving to general consult")
        except json.JSONDecodeError:
            logger.error(f" error parcing  JSON: {msg.value()}")
        except Exception as e:
            logger.error(f"unexpected error: {e}")

except KeyboardInterrupt:
    logging.info("stop kafka")

finally:
    consumer.close()





