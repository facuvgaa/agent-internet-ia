package com.cliente.cliente_back.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import com.cliente.cliente_back.dto.ChatRequestDTO;

@Controller
public class ChatController {
    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
    
    @MessageMapping("/chat")  
    public void recibirMensaje(@Payload ChatRequestDTO request) {
        String mensaje = String.format(
            "{\"contenido\": \"%s\", \"customer_id\": \"%s\"}",
            request.getContenido(), request.getCustomerId()
        );
        kafkaTemplate.send("consultas.usuario", mensaje);
    }
}
