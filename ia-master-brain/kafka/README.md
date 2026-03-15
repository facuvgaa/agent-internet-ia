# Cómo enviar y ver mensajes de Kafka

## Requisitos

- Kafka corriendo (por ejemplo `docker compose up -d kafka` en la raíz del repo).
- Para **recibir respuestas del LLM**: el consumer del brain debe estar corriendo en otra terminal.

---

## Opción 1: Scripts Python (recomendado)

Desde la carpeta **ia-master-brain** (para que resuelvan los imports si hace falta, o desde la raíz con `-m`):

### Enviar un mensaje y ver la respuesta del LLM

```bash
cd ia-master-brain
python -m kafka.send_test_message "¿Cuánto me cobran de internet?"
python -m kafka.send_test_message "Quiero ver mi factura" 2
```

El primer argumento es el texto; el segundo (opcional) es `customer_id` (por defecto 1). El script envía a `consultas.usuario` y espera la primera respuesta en `respuestas.agente`.

### Solo ver mensajes de un tópico

```bash
# Ver todo lo que llega a consultas (lo que envían los usuarios)
python -m kafka.ver_mensajes consultas.usuario

# Ver todo lo que publica el brain (respuestas)
python -m kafka.ver_mensajes respuestas.agente
```

Ctrl+C para salir.

---

## Opción 2: CLI de Kafka con Docker

Si tenés Kafka en Docker (contenedor `kafka-internet`):

### Enviar un mensaje (una línea JSON por mensaje)

```bash
docker exec -it kafka-internet kafka-console-producer \
  --bootstrap-server localhost:9092 \
  --topic consultas.usuario
```

Luego escribís una línea con el JSON y Enter, por ejemplo:

```json
{"contenido": "¿Cuánto me cobran?", "customer_id": "1"}
```

Ctrl+C para salir del producer.

### Ver mensajes de un tópico

```bash
# Ver respuestas del agente
docker exec -it kafka-internet kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic respuestas.agente \
  --from-beginning

# Ver consultas entrantes
docker exec -it kafka-internet kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic consultas.usuario \
  --from-beginning
```

---

## Flujo para probar el LLM

1. **Terminal 1**: arrancar el consumer del brain  
   `cd ia-master-brain && python -m kafka.consumer`

2. **Terminal 2**: enviar un mensaje de prueba y ver la respuesta  
   `cd ia-master-brain && python -m kafka.send_test_message "¿Cuánto me cobran?"`

O en la terminal 2 solo escuchar respuestas:  
`python -m kafka.ver_mensajes respuestas.agente`  
y en una **Terminal 3** enviar con el script o con el producer de Docker.
