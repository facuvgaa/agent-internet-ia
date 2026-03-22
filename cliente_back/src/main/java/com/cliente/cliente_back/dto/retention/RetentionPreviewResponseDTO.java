package com.cliente.cliente_back.dto.retention;

import java.math.BigDecimal;

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
