package com.cliente.cliente_back.dto;


public record TicketRequestDTO(
    Long id,
    Long customerId,
    String subject,
    String priority
) {}
