package com.cliente.cliente_back.application.usecase;

import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import com.cliente.cliente_back.application.dto.PaymentPromiseDTO;
import com.cliente.cliente_back.domain.model.Billing;
import com.cliente.cliente_back.domain.model.PaymentPromise;
import com.cliente.cliente_back.domain.model.PaymentPromiseStatus;
import com.cliente.cliente_back.domain.port.in.PaymentPromiseUseCase;
import com.cliente.cliente_back.domain.port.out.BillingRepositoryPort;
import com.cliente.cliente_back.domain.port.out.PaymentPromiseRepositoryPort;
import com.cliente.cliente_back.infrastructure.persistence.mapper.PaymentPromiseEntityMapper;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentPromiseUseCaseImpl implements PaymentPromiseUseCase {

    private final PaymentPromiseRepositoryPort paymentPromiseRepositoryPort;
    private final BillingRepositoryPort billingRepositoryPort;
    private final PaymentPromiseEntityMapper paymentPromiseMapper;

    @Override
    public PaymentPromiseDTO createPromise(PaymentPromiseDTO dto) {
        if (dto.customerId() == null || dto.billingId() == null || dto.promiseUntil() == null) {
            throw new IllegalArgumentException("customerId, billingId y promiseUntil son obligatorios");
        }

        Billing billing = billingRepositoryPort.findById(dto.billingId())
                .orElseThrow(() -> new IllegalArgumentException("No existe la factura indicada"));

        if (billing.getCustomerId() == null || !billing.getCustomerId().equals(dto.customerId())) {
            throw new IllegalArgumentException("La factura no pertenece al customerId indicado");
        }

        if (billing.isPaid()) {
            throw new IllegalArgumentException("No se puede crear promesa sobre una factura pagada");
        }

        if (!dto.promiseUntil().isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("promiseUntil debe ser una fecha futura");
        }

        PaymentPromise promise = PaymentPromise.builder()
                .customerId(dto.customerId())
                .billingId(dto.billingId())
                .promiseUntil(dto.promiseUntil())
                .status(PaymentPromiseStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();

        PaymentPromise saved = paymentPromiseRepositoryPort.save(promise);
        return paymentPromiseMapper.toDto(saved);
    }
}
