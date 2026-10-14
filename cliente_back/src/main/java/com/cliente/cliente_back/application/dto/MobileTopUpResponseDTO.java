package com.cliente.cliente_back.application.dto;

import com.cliente.cliente_back.domain.model.*;


public record MobileTopUpResponseDTO(
    String benefitId,
    Long ticketId,
    Long customerId,
    String msisdn,
    int dataGb,
    BenefitStatus status,
    String validUntil,
    String nextEligibleAt,
    String message
) {}
