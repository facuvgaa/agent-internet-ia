package com.cliente.cliente_back.kafka;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import java.util.Map;
@Component
public class ChatKafkaListener {
    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    @KafkaListener(topics = "respuestas.agente", groupId = "websocket-bridge")
    public void escucharRespuesta(String mensaje) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode json = mapper.readTree(mensaje);
            String customerId = json.get("customer_id").asText();
            String respuesta  = json.get("respuesta").asText();
            messagingTemplate.convertAndSendToUser(
                customerId,
                "/queue/chat",
                Map.of("respuesta", respuesta)
            );
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
