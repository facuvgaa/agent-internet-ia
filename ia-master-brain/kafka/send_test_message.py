#!/usr/bin/env python3
"""
Envía un mensaje al tópico consultas.usuario y espera la respuesta en respuestas.agente.
Uso (desde la raíz del repo o desde ia-master-brain):
  python -m kafka.send_test_message "¿Cuánto me cobran de internet?"
  python -m kafka.send_test_message "Quiero ver mi factura" 2
"""
import json
import sys
import time
from confluent_kafka import Producer, Consumer

BOOTSTRAP = "localhost:9092"
TOPIC_CONSULTAS = "consultas.usuario"
TOPIC_RESPUESTAS = "respuestas.agente"


def main():
    contenido = sys.argv[1] if len(sys.argv) > 1 else "¿si quisiera reclamar la factura, por que no puedo pagarla??"
    customer_id = sys.argv[2] if len(sys.argv) > 2 else "1"

    payload = {
        "contenido": contenido,
        "customer_id": customer_id,
    }

    # 1. Enviar mensaje a consultas.usuario
    producer = Producer({"bootstrap.servers": BOOTSTRAP})
    producer.produce(TOPIC_CONSULTAS, value=json.dumps(payload).encode("utf-8"))
    producer.flush()
    print(f"[ENVIADO] Tópico: {TOPIC_CONSULTAS}")
    print(f"  Mensaje: {json.dumps(payload, ensure_ascii=False)}")
    print()

    # 2. Suscribirse a respuestas.agente y esperar una respuesta (timeout 60 s)
    consumer = Consumer({
        "bootstrap.servers": BOOTSTRAP,
        "group.id": "test-consumer-" + str(int(time.time())),
        "auto.offset.reset": "latest",
    })
    consumer.subscribe([TOPIC_RESPUESTAS])
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
