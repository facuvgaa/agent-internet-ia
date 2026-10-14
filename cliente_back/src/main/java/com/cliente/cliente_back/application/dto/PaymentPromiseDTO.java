package com.cliente.cliente_back.application.dto;

import com.cliente.cliente_back.domain.model.*;

import java.time.LocalDateTime;

public record PaymentPromiseDTO(
    Long id,
    Long customerId,
    Long billingId,
    LocalDateTime promiseUntil,
    PaymentPromiseStatus status
) {}
