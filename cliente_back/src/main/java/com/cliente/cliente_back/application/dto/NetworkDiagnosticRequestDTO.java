package com.cliente.cliente_back.application.dto;

import com.cliente.cliente_back.domain.model.*;

public record NetworkDiagnosticRequestDTO(
    Long customerId,
    Long serviceId,     
    String channel
){}
