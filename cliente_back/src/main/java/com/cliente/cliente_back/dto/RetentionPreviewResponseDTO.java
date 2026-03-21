package com.cliente.cliente_back.dto;

import java.math.BigDecimal;

/**
 * Montos estimados sobre {@code basePrice} del servicio + textos para el agente / LLM.
 * Si no hay precio base en el servicio, los importes vienen null y igual se devuelven % y narrativa.
 */
public record RetentionPreviewResponseDTO(
        Long customerId,
        Long serviceId,
        int level,
        int discountPercent,
        int durationMonths,
        String currency,
        BigDecimal baseMonthlyAmount,
        BigDecimal estimatedMonthlyAfterDiscount,
        int appPaymentCashbackPercent,
        String nextBillSummary,
        String appCashbackExplanation) {}
