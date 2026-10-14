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
public class RetentionApplication {
    private Long id;
    private Long customerId;
    private Long serviceId;
    private int level;
    private int discountPercent;
    private int durationMonths;
    private LocalDateTime validFrom;
    private LocalDateTime validUntil;
    private String idempotencyKey;
    private String channel;
    private String acceptedTermsVersion;
    private String notes;
    private RetentionApplicationStatus status;
    private LocalDateTime createdAt;
}
