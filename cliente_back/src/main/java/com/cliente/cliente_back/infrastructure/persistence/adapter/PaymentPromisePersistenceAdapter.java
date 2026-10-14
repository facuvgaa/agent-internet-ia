package com.cliente.cliente_back.infrastructure.persistence.adapter;

import java.util.Optional;
import org.springframework.stereotype.Component;
import com.cliente.cliente_back.domain.model.PaymentPromise;
import com.cliente.cliente_back.domain.port.out.PaymentPromiseRepositoryPort;
import com.cliente.cliente_back.infrastructure.persistence.mapper.PaymentPromiseEntityMapper;
import com.cliente.cliente_back.infrastructure.persistence.repository.PaymentPromiseRepository;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PaymentPromisePersistenceAdapter implements PaymentPromiseRepositoryPort {

    private final PaymentPromiseRepository paymentPromiseRepository;
    private final PaymentPromiseEntityMapper paymentPromiseMapper;

    @Override
    public PaymentPromise save(PaymentPromise paymentPromise) {
        var entity = paymentPromiseMapper.toEntity(paymentPromise);
        var saved = paymentPromiseRepository.save(entity);
        return paymentPromiseMapper.toDomain(saved);
    }

    @Override
    public Optional<PaymentPromise> findById(Long id) {
        return paymentPromiseRepository.findById(id).map(paymentPromiseMapper::toDomain);
    }
}
