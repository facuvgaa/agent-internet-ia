package com.cliente.cliente_back.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cliente.cliente_back.domain.model.RetentionTierSpec;
import com.cliente.cliente_back.domain.service.RetentionPricingService;

class RetentionPricingServiceTest {

    @Test
    @DisplayName("calculateDiscountedPrice: calcula correctamente 50% de descuento")
    void calculateDiscountedPrice_fiftyPercent() {
        BigDecimal base = new BigDecimal("10000.00");
        BigDecimal discounted = RetentionPricingService.calculateDiscountedPrice(base, RetentionTierSpec.LEVEL_2);

        assertEquals(new BigDecimal("5000.00"), discounted);
    }

    @Test
    @DisplayName("calculateDiscountedPrice: calcula correctamente 25% de descuento")
    void calculateDiscountedPrice_twentyFivePercent() {
        BigDecimal base = new BigDecimal("10000.00");
        BigDecimal discounted = RetentionPricingService.calculateDiscountedPrice(base, RetentionTierSpec.LEVEL_1);

        assertEquals(new BigDecimal("7500.00"), discounted);
    }

    @Test
    @DisplayName("calculateDiscountedPrice: retorna null cuando el basePrice es null o negativo")
    void calculateDiscountedPrice_nullOrNegative() {
        assertNull(RetentionPricingService.calculateDiscountedPrice(null, RetentionTierSpec.LEVEL_1));
        assertNull(RetentionPricingService.calculateDiscountedPrice(new BigDecimal("-10.00"), RetentionTierSpec.LEVEL_1));
    }

    @Test
    @DisplayName("buildNextBillSummary: formatea texto descriptivo con montos")
    void buildNextBillSummary_withAmounts() {
        String summary = RetentionPricingService.buildNextBillSummary(
                new BigDecimal("10000.00"), new BigDecimal("5000.00"), 50, 3);

        assertTrue(summary.contains("50%"));
        assertTrue(summary.contains("3 meses"));
        assertTrue(summary.contains("5000.00"));
    }
}
