package com.cliente.cliente_back.services;

import com.cliente.cliente_back.dto.PaymentPromiseDTO;

public interface PaymentPromiseService {
    PaymentPromiseDTO createPromise(PaymentPromiseDTO dto);
}
