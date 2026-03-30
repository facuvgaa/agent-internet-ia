import json
import sys
import time
from confluent_kafka import Producer, Consumer

BOOTSTRAP = "localhost:9092"
TOPIC_CONSULTAS = "consultas.usuario"
TOPIC_RESPUESTAS = "respuestas.agente"


def main():
    contenido   = sys.argv[1] if len(sys.argv) > 1 else "si quiero reclamar, tengo el numero de comprobante si queres, pasa que me aparece como impaga y me pueden cortar el servicio"
    customer_id = sys.argv[2] if len(sys.argv) > 2 else "1"

    payload = {"contenido": contenido, "customer_id": customer_id}

    # 1. Suscribirse PRIMERO, antes de enviar
    consumer = Consumer({
        "bootstrap.servers": BOOTSTRAP,
        "group.id":          "test-consumer-" + str(int(time.time())),
        "auto.offset.reset": "latest",  # latest está bien si suscribimos antes de producir
    })
    consumer.subscribe([TOPIC_RESPUESTAS])

    # darle tiempo a Kafka para registrar la suscripción
    time.sleep(2)

    # 2. Recién ahora enviar el mensaje
    producer = Producer({"bootstrap.servers": BOOTSTRAP})
    producer.produce(TOPIC_CONSULTAS, value=json.dumps(payload).encode("utf-8"))
    producer.flush()
    print(f"[ENVIADO] Tópico: {TOPIC_CONSULTAS}")
    print(f"  Mensaje: {json.dumps(payload, ensure_ascii=False)}")
    print()

    # 3. Esperar respuesta
    print(f"[ESPERANDO] Respuesta en {TOPIC_RESPUESTAS} (timeout 60 s)...")
    start = time.time()
    while time.time() - start < 60:
        msg = consumer.poll(1.0)
        if msg is None:
            continue
        if msg.error():
            print(f"Error: {msg.error()}")
            continue
        try:
            data = json.loads(msg.value().decode("utf-8"))
            print("[RESPUESTA]")
            print(json.dumps(data, indent=2, ensure_ascii=False))
            break
        except Exception as e:
            print(f"Error leyendo mensaje: {e}")
    else:
        print("Timeout: no llegó respuesta. ¿Está corriendo el consumer del brain?")

    consumer.close()


if __name__ == "__main__":
    main()