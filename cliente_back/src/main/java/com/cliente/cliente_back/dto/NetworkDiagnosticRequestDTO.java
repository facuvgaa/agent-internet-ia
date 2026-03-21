package com.cliente.cliente_back.dto;

public record NetworkDiagnosticRequestDTO(
    Long customerId,
    Long serviceId,     
    String channel
){}
