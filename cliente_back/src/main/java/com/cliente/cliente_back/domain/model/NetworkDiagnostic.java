package com.cliente.cliente_back.domain.model;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NetworkDiagnostic {
    private Long id;
    private Long customerId;
    private Long serviceId;
    private String channel;
    private NetworkHealthStatus status;
    private boolean massiveOutage;
    private String zoneOrNode;
    private Integer etaMinutes;
    private boolean canRemoteReset;
    private String incidentId;
    private String message;
    private LocalDateTime createdAt;
}
