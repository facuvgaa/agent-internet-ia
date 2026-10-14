package com.cliente.cliente_back.application.dto;

import com.cliente.cliente_back.domain.model.*;

public record ConnectionResetRequestDTO(
    Long customerId,
    Long serviceId,          
    String reason 
) {}
