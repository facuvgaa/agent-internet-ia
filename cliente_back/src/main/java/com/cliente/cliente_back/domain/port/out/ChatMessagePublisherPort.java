package com.cliente.cliente_back.domain.port.out;

public interface ChatMessagePublisherPort {
    void publishUserQuery(String customerId, String content);
}

