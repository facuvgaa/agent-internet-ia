package com.cliente.cliente_back.infrastructure.messaging.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChatKafkaListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper mapper = new ObjectMapper();

    @KafkaListener(topics = "respuestas.agente", groupId = "websocket-bridge")
    public void escucharRespuesta(String mensaje) {
        try {
            JsonNode json = mapper.readTree(mensaje);
            String customerId = json.get("customer_id").asText();
            String respuesta = json.get("respuesta").asText();
            messagingTemplate.convertAndSendToUser(
                customerId,
                "/queue/chat",
                Map.of("respuesta", respuesta)
            );
        } catch (Exception e) {
            log.error("Error processing agent response from Kafka", e);
        }
    }
}

