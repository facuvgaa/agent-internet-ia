
package com.cliente.cliente_back.application.dto;

import com.cliente.cliente_back.domain.model.*;

public record ConnectionResetResponseDTO(
    String resetJobId,
    Long customerId,
    Long serviceId,
    ResetJobStatus status,
    String message,
    Integer estimatedSeconds
){}
