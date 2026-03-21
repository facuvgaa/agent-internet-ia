package com.cliente.cliente_back.dto;

public record AvailableOfferingItemDTO(
        String offeringCode,
        String displayName,
        String description,
        String quotaServiceType,
        int maxAllowedActiveOfType,
        int currentActiveCountOfType,
        boolean eligible,
        String ineligibilityReason) {}
