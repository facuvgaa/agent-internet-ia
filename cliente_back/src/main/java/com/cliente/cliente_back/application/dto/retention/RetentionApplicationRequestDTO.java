package com.cliente.cliente_back.application.dto.retention;

import com.cliente.cliente_back.domain.model.*;

public record RetentionApplicationRequestDTO(
        Long customerId,
        Long serviceId,
        int level,
        String idempotencyKey,
        String channel,
        String acceptedTermsVersion,
        String notes) {}
