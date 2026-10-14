package com.cliente.cliente_back.application.dto.retention;

import com.cliente.cliente_back.domain.model.*;

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
