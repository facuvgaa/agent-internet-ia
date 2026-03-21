package com.cliente.cliente_back.dto;

import java.math.BigDecimal;

public record MobileTopUpRequestDTO(
    Long customerId,
    String msisdn,
    BigDecimal amount,
    String currency,
    String reason,
    String relatedIncidentId,
    Long relatedBillingId
) {}
