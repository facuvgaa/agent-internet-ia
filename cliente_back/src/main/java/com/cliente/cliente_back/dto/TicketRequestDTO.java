package com.cliente.cliente_back.dto;


public record TicketRequestDTO (
    Long customerId,
    String subject,
    String priority
){}
