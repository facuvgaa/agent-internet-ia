package com.cliente.cliente_back.infrastructure.web.controller;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import com.cliente.cliente_back.application.dto.ChatRequestDTO;
import com.cliente.cliente_back.domain.port.out.ChatMessagePublisherPort;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class ChatController {

    private final ChatMessagePublisherPort chatMessagePublisherPort;

    @MessageMapping("/chat")
    public void recibirMensaje(@Payload ChatRequestDTO request) {
        chatMessagePublisherPort.publishUserQuery(request.getCustomerId(), request.getContenido());
    }
}

