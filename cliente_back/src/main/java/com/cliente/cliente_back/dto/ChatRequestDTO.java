package com.cliente.cliente_back.dto;

import lombok.Data;

@Data
public class ChatRequestDTO {
    private String contenido;
    private String customerId;
}
