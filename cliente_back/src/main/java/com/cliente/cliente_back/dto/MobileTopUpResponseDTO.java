package com.cliente.cliente_back.dto;

import java.math.BigDecimal;
public record MobileTopUpResponseDTO(
    String benefitId,
    Long customerId,
    String msisdn,
    BigDecimal amount,
    String currency,
    BenefitStatus status,
    String validUntil,
    String nextEligibleAt,
    String message
) {}
