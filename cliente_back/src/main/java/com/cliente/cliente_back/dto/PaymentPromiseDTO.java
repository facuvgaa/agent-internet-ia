package com.cliente.cliente_back.dto;

import java.time.LocalDateTime;

public record PaymentPromiseDTO(
    Long id,
    Long customerId,
    Long billingId,
    LocalDateTime promiseUntil,
    PaymentPromiseStatus status
) {}
