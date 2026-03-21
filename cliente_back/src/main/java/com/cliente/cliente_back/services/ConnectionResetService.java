package com.cliente.cliente_back.services;

import com.cliente.cliente_back.dto.ConnectionResetRequestDTO;
import com.cliente.cliente_back.dto.ConnectionResetResponseDTO;

public interface ConnectionResetService {

    /** Stub: valida cliente/servicio, registra un reset simulado en cola. */
    ConnectionResetResponseDTO requestReset(ConnectionResetRequestDTO request);
}
