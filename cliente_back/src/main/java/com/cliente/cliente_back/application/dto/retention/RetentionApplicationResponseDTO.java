package com.cliente.cliente_back.application.dto.retention;

import com.cliente.cliente_back.domain.model.*;


public record RetentionApplicationResponseDTO(
        Long applicationId,
        Long customerId,
        Long serviceId,
        int level,
        int discountPercent,
        int durationMonths,
        String validFrom,
        String validUntil,
        String status,
        String message) {}
