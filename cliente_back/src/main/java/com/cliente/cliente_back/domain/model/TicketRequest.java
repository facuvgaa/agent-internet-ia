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
public class TicketRequest {
    private Long id;
    private Long customerId;
    private String subject;
    private String status;
    private String priority;
    private LocalDateTime createdAt;
    private LocalDateTime closedAt;

    public boolean isClosed() {
        return closedAt != null || "CLOSED".equalsIgnoreCase(status);
    }
}
