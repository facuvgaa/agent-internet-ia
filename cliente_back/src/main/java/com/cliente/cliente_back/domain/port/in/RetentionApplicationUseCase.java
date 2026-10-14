package com.cliente.cliente_back.domain.port.in;

import com.cliente.cliente_back.application.dto.retention.RetentionApplicationRequestDTO;
import com.cliente.cliente_back.application.dto.retention.RetentionApplicationResponseDTO;

public interface RetentionApplicationUseCase {
    RetentionApplicationResponseDTO apply(RetentionApplicationRequestDTO request);
}

