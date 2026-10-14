package com.cliente.cliente_back.application.dto.addon;

import com.cliente.cliente_back.domain.model.*;


public record AddonSubscriptionRequestDTO(
        Long customerId,
        String addonCode,
        Long serviceId,
        String idempotencyKey,
        String channel,
        String acceptedTermsVersion,
        String notes) {}
