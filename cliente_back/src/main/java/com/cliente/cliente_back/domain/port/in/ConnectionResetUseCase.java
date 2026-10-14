package com.cliente.cliente_back.domain.port.in;

import com.cliente.cliente_back.application.dto.ConnectionResetRequestDTO;
import com.cliente.cliente_back.application.dto.ConnectionResetResponseDTO;

public interface ConnectionResetUseCase {
    ConnectionResetResponseDTO requestReset(ConnectionResetRequestDTO request);
}

