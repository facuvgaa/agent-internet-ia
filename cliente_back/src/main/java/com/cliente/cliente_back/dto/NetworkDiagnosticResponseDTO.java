package com.cliente.cliente_back.dto;

public record NetworkDiagnosticResponseDTO(
    String diagnosticId,
    Long customerId,
    Long serviceId,
    NetworkHealthStatus status,
    boolean massiveOutage,
    String zoneOrNode,          
    Integer etaMinutes,         
    boolean canRemoteReset,
    String incidentId,          
    String message
) {}
