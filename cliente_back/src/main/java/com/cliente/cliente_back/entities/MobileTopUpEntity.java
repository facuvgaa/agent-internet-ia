package com.cliente.cliente_back.entities;

import java.time.LocalDateTime;

import com.cliente.cliente_back.dto.BenefitStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Data
@Table(name = "mobile_topups")
public class MobileTopUpEntity {

    /** Paquete fijo de compensación (GB). */
    public static final int FIXED_DATA_GB = 10;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "benefit_id", nullable = false, unique = true, length = 64)
    private String benefitId;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "msisdn", nullable = false, length = 32)
    private String msisdn;

    @Column(name = "data_gb", nullable = false)
    private Integer dataGb = FIXED_DATA_GB;

    @Column(length = 64)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BenefitStatus status;

    @Column(name = "valid_until")
    private LocalDateTime validUntil;

    @Column(name = "next_eligible_at")
    private LocalDateTime nextEligibleAt;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
