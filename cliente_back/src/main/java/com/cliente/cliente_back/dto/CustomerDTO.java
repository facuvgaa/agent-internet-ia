package com.cliente.cliente_back.dto;

public record CustomerDTO(
    Long id,
    String name,
    String email,
    String status,
    String phone
){}
