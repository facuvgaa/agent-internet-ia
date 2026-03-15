#!/usr/bin/env python3
"""
Escucha mensajes de un tópico y los imprime (Ctrl+C para salir).
Uso (desde ia-master-brain):
  python -m kafka.ver_mensajes consultas.usuario
  python -m kafka.ver_mensajes respuestas.agente
"""
import json
import sys
from confluent_kafka import Consumer

BOOTSTRAP = "localhost:9092"


def main():
    topic = sys.argv[1] if len(sys.argv) > 1 else "consultas.usuario"
    if topic not in ("consultas.usuario", "respuestas.agente"):
        print("Uso: python -m kafka.ver_mensajes consultas.usuario | respuestas.agente")
        sys.exit(1)

    consumer = Consumer({
        "bootstrap.servers": BOOTSTRAP,
        "group.id": "ver-mensajes-" + topic.replace(".", "-"),
        "auto.offset.reset": "earliest",
    })
    consumer.subscribe([topic])
    print(f"Escuchando tópico: {topic} (Ctrl+C para salir)\n")
    try:
        while True:
            msg = consumer.poll(1.0)
            if msg is None:
                continue
            if msg.error():
                print(f"Error: {msg.error()}")
                continue
            try:
                raw = msg.value().decode("utf-8")
                data = json.loads(raw)
                print(json.dumps(data, indent=2, ensure_ascii=False))
                print("-" * 40)
            except json.JSONDecodeError:
                print(raw)
                print("-" * 40)
    except KeyboardInterrupt:
        print("\nSalida.")
    finally:
        consumer.close()


if __name__ == "__main__":
    main()
