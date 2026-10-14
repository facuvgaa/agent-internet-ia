package com.cliente.cliente_back.application.dto;

import com.cliente.cliente_back.domain.model.*;

public record AvailableOfferingItemDTO(
        String offeringCode,
        String displayName,
        String description,
        String quotaServiceType,
        int maxAllowedActiveOfType,
        int currentActiveCountOfType,
        boolean eligible,
        String ineligibilityReason) {}
