package com.cliente.cliente_back.application.dto;

import com.cliente.cliente_back.domain.model.*;

import lombok.Data;

@Data
public class ChatRequestDTO {
    private String contenido;
    private String customerId;
}
