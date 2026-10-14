package com.cliente.cliente_back.domain.service;

import java.time.LocalDateTime;

public final class MobileTopUpCooldownValidator {

    private MobileTopUpCooldownValidator() {}

    public static void validateCooldown(LocalDateTime nextEligibleAt, LocalDateTime now) {
        if (nextEligibleAt != null && now.isBefore(nextEligibleAt)) {
            throw new IllegalArgumentException(
                    "Esta línea ya recibió una recarga de compensación recientemente. Podrá solicitar otra a partir de "
                            + nextEligibleAt);
        }
    }
}

