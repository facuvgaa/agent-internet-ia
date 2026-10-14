package com.cliente.cliente_back.domain.port.in;

import java.util.Optional;
import com.cliente.cliente_back.application.dto.CustomerDTO;

public interface CustomerUseCase {
    Optional<CustomerDTO> getCustomerById(Long id);
}

