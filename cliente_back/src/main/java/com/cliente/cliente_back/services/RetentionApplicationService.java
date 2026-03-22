package com.cliente.cliente_back.services;

import com.cliente.cliente_back.dto.retention.RetentionApplicationRequestDTO;
import com.cliente.cliente_back.dto.retention.RetentionApplicationResponseDTO;

public interface RetentionApplicationService {

    /**
     * Registra en BD el acuerdo de retención (nivel del catálogo). No modifica {@code services};
     * el % y meses se toman siempre de {@link com.cliente.cliente_back.retention.RetentionTierSpec}.
     */
    RetentionApplicationResponseDTO apply(RetentionApplicationRequestDTO request);
}
