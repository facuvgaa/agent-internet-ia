package com.cliente.cliente_back.application.dto.retention;

import com.cliente.cliente_back.domain.model.*;

import java.util.List;

public record RetentionEligibilityResponseDTO(
        Long customerId,
        Long serviceId,
        boolean eligible,
        List<Integer> allowedLevels,
        int appPaymentCashbackPercent,
        String message) {}
