package com.cliente.cliente_back.dto;

public record MobileTopUpRequestDTO(
    Long customerId,
    Long ticketId,
    String msisdn,
    String reason
) {}
