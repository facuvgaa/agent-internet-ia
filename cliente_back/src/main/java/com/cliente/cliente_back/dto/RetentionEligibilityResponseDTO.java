package com.cliente.cliente_back.dto;

import java.util.List;

/**
 * Qué niveles puede cotizar/aplicar el agente para este cliente (y servicio, si se informó).
 */
public record RetentionEligibilityResponseDTO(
        Long customerId,
        Long serviceId,
        boolean eligible,
        List<Integer> allowedLevels,
        int appPaymentCashbackPercent,
        String message) {}
