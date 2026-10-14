package com.cliente.cliente_back.domain.port.in;

import java.util.List;
import com.cliente.cliente_back.application.dto.ServicesDTO;

public interface ServicesUseCase {
    List<ServicesDTO> findAllByCustomerId(Long customerId);
}

