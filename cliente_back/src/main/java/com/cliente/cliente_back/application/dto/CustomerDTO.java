package com.cliente.cliente_back.application.dto;

import com.cliente.cliente_back.domain.model.*;

public record CustomerDTO(
    Long id,
    String name,
    String email,
    String status,
    String phone
){}
