package com.cliente.cliente_back.services;

import java.util.List;

import com.cliente.cliente_back.dto.BillingDTO;

public interface BillingService {
    List<BillingDTO> findAllBilling(Long customerId);
}
