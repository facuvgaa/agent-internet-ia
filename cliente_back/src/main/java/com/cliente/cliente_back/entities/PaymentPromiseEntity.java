package com.cliente.cliente_back.entities;

import java.time.LocalDateTime;

import com.cliente.cliente_back.dto.PaymentPromiseStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "payment_promises")
@Data
public class PaymentPromiseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "billing_id", nullable = false)
    private Long billingId;

    @Column(name = "promise_until", nullable = false)
    private LocalDateTime promiseUntil;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentPromiseStatus status = PaymentPromiseStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
