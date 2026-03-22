package com.cliente.cliente_back.dto.retention;

import java.util.List;

public record RetentionEligibilityResponseDTO(
        Long customerId,
        Long serviceId,
        boolean eligible,
        List<Integer> allowedLevels,
        int appPaymentCashbackPercent,
        String message) {}
