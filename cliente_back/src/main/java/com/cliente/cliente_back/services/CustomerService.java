package com.cliente.cliente_back.services;

import java.util.Optional;

import com.cliente.cliente_back.dto.CustomerDTO;

public interface CustomerService {
    Optional<CustomerDTO> getCustomerById(Long id);
}
