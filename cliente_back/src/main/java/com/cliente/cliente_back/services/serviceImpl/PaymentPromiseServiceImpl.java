package com.cliente.cliente_back.services.serviceImpl;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.cliente.cliente_back.dto.BillingStatus;
import com.cliente.cliente_back.dto.PaymentPromiseDTO;
import com.cliente.cliente_back.dto.PaymentPromiseStatus;
import com.cliente.cliente_back.entities.BillingEntity;
import com.cliente.cliente_back.mappers.PaymentPromiseMapper;
import com.cliente.cliente_back.repositories.BillingRepository;
import com.cliente.cliente_back.repositories.PaymentPromiseRepository;
import com.cliente.cliente_back.services.PaymentPromiseService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentPromiseServiceImpl implements PaymentPromiseService {
    private final PaymentPromiseRepository paymentPromiseRepository;
    private final PaymentPromiseMapper paymentPromiseMapper;
    private final BillingRepository billingRepository;

    @Override
    public PaymentPromiseDTO createPromise(PaymentPromiseDTO dto) {
        if (dto.customerId() == null || dto.billingId() == null || dto.promiseUntil() == null) {
            throw new IllegalArgumentException("customerId, billingId y promiseUntil son obligatorios");
        }

        BillingEntity billing = billingRepository.findById(dto.billingId())
            .orElseThrow(() -> new IllegalArgumentException("No existe la factura indicada"));

        if (dto.customerId() == null
                || billing.getCustomerId() == null
                || !billing.getCustomerId().equals(String.valueOf(dto.customerId()))) {
            throw new IllegalArgumentException("La factura no pertenece al customerId indicado");
        }

        if (billing.getStatus() == BillingStatus.PAID) {
            throw new IllegalArgumentException("No se puede crear promesa sobre una factura pagada");
        }

        if (!dto.promiseUntil().isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("promiseUntil debe ser una fecha futura");
        }

        var entity = paymentPromiseMapper.toEntity(dto);
        entity.setId(null);
        entity.setStatus(PaymentPromiseStatus.ACTIVE);
        var saved = paymentPromiseRepository.save(entity);
        return paymentPromiseMapper.toDto(saved);
    }
}
