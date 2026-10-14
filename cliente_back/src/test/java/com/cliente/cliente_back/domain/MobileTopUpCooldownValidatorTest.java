package com.cliente.cliente_back.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cliente.cliente_back.domain.service.MobileTopUpCooldownValidator;

class MobileTopUpCooldownValidatorTest {

    @Test
    @DisplayName("validateCooldown: permite recarga cuando nextEligibleAt ya pasó")
    void validateCooldown_pastDate() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime past = now.minusDays(1);

        assertDoesNotThrow(() -> MobileTopUpCooldownValidator.validateCooldown(past, now));
    }

    @Test
    @DisplayName("validateCooldown: permite recarga cuando nextEligibleAt es nulo")
    void validateCooldown_nullDate() {
        assertDoesNotThrow(() -> MobileTopUpCooldownValidator.validateCooldown(null, LocalDateTime.now()));
    }

    @Test
    @DisplayName("validateCooldown: lanza excepción cuando aún está en periodo de cooldown")
    void validateCooldown_futureDate() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime future = now.plusDays(10);

        assertThrows(IllegalArgumentException.class,
                () -> MobileTopUpCooldownValidator.validateCooldown(future, now));
    }
}
