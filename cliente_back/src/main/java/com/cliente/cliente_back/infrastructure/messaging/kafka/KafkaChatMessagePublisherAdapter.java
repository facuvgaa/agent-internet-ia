package com.cliente.cliente_back.infrastructure.messaging.kafka;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import com.cliente.cliente_back.domain.port.out.ChatMessagePublisherPort;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class KafkaChatMessagePublisherAdapter implements ChatMessagePublisherPort {

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Override
    public void publishUserQuery(String customerId, String content) {
        String mensaje = String.format(
            "{\"contenido\": \"%s\", \"customer_id\": \"%s\"}",
            content, customerId
        );
        kafkaTemplate.send("consultas.usuario", mensaje);
    }
}

