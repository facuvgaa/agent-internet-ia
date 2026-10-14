package com.cliente.cliente_back.application.dto;

import com.cliente.cliente_back.domain.model.*;

public record MobileTopUpRequestDTO(
    Long customerId,
    Long ticketId,
    String msisdn,
    String reason
) {}
