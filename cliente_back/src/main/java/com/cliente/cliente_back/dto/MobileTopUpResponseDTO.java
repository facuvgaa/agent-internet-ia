package com.cliente.cliente_back.dto;

public record MobileTopUpResponseDTO( 
    String benefitId,
    Long customerId,
    java.math.BigDecimal amount,
    String currency,
    BenefitStatus status,
    String validUntil,     
    String message
) {}
