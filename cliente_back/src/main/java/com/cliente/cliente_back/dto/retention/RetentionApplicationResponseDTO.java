package com.cliente.cliente_back.dto.retention;


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
