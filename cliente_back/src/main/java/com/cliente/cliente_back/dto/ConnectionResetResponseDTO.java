
package com.cliente.cliente_back.dto;

public record ConnectionResetResponseDTO(
    String resetJobId,
    Long customerId,
    Long serviceId,
    ResetJobStatus status,
    String message,
    Integer estimatedSeconds
){}
