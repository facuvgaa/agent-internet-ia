package com.cliente.cliente_back.dto;

public record ConnectionResetRequestDTO(
    Long customerId,
    Long serviceId,          
    String reason 
) {}
