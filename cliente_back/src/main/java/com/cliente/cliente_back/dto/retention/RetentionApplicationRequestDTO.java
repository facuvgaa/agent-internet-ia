package com.cliente.cliente_back.dto.retention;

public record RetentionApplicationRequestDTO(
        Long customerId,
        Long serviceId,
        int level,
        String idempotencyKey,
        String channel,
        String acceptedTermsVersion,
        String notes) {}
