package com.cliente.cliente_back.dto.addon;


public record AddonSubscriptionRequestDTO(
        Long customerId,
        String addonCode,
        Long serviceId,
        String idempotencyKey,
        String channel,
        String acceptedTermsVersion,
        String notes) {}
