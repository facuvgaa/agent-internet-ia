package com.cliente.cliente_back.domain.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import com.cliente.cliente_back.domain.model.RetentionTierSpec;

public final class RetentionPricingService {

    private RetentionPricingService() {}

    public static BigDecimal calculateDiscountedPrice(BigDecimal basePrice, RetentionTierSpec tier) {
        if (basePrice == null || basePrice.signum() < 0) {
            return null;
        }
        BigDecimal factor = BigDecimal.ONE.subtract(
                BigDecimal.valueOf(tier.discountPercent())
                        .divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
        return basePrice.multiply(factor).setScale(2, RoundingMode.HALF_UP);
    }

    public static String buildNextBillSummary(
            BigDecimal base, BigDecimal after, int discountPercent, int durationMonths) {
        if (base != null && after != null) {
            return String.format(
                    "Con el %d%% de descuento por %d meses, el mes próximo la cuota estimada sería de %s "
                            + "(antes del cashback por app).",
                    discountPercent, durationMonths, after);
        }
        return String.format(
                "Con el %d%% de descuento por %d meses, el mes próximo pagará aproximadamente el "
                        + "precio de lista menos ese porcentaje (definir monto cuando el servicio tenga basePrice).",
                discountPercent, durationMonths);
    }
}
