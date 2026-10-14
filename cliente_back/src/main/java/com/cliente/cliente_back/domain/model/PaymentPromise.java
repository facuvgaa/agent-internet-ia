package com.cliente.cliente_back.domain.model;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentPromise {
    private Long id;
    private Long customerId;
    private Long billingId;
    private LocalDateTime promiseUntil;
    private PaymentPromiseStatus status;
    private LocalDateTime createdAt;
}
