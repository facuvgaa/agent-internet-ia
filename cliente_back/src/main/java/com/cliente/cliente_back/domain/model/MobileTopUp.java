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
public class MobileTopUp {
    public static final int FIXED_DATA_GB = 10;
    public static final int COOLDOWN_DAYS = 30;
    public static final int PACKAGE_VALIDITY_DAYS = 30;

    private Long id;
    private String benefitId;
    private Long ticketId;
    private Long customerId;
    private String msisdn;
    private Integer dataGb;
    private String reason;
    private BenefitStatus status;
    private LocalDateTime validUntil;
    private LocalDateTime nextEligibleAt;
    private String message;
    private LocalDateTime createdAt;
}

