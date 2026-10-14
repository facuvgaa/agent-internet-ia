package com.cliente.cliente_back.domain.port.out;

import java.util.Optional;
import com.cliente.cliente_back.domain.model.PaymentPromise;

public interface PaymentPromiseRepositoryPort {
    PaymentPromise save(PaymentPromise paymentPromise);
    Optional<PaymentPromise> findById(Long id);
}

