package com.cliente.cliente_back.domain.port.in;

import com.cliente.cliente_back.application.dto.PaymentPromiseDTO;

public interface PaymentPromiseUseCase {
    PaymentPromiseDTO createPromise(PaymentPromiseDTO dto);
}

