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
public class ConnectionReset {
    private Long id;
    private String resetJobId;
    private Long customerId;
    private Long serviceId;
    private String reason;
    private ResetJobStatus status;
    private String message;
    private Integer estimatedSeconds;
    private LocalDateTime createdAt;
}

