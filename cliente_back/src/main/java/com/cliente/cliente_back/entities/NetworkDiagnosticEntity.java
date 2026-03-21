package com.cliente.cliente_back.entities;

import java.time.LocalDateTime;

import com.cliente.cliente_back.dto.NetworkHealthStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name="network_diagnostics")
public class NetworkDiagnosticEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;
    @Column(name = "service_id")
    private Long serviceId;
    @Column(name = "channel", length = 32)
    private String channel;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NetworkHealthStatus status;
    @Column(name = "massive_outage", nullable = false)
    private boolean massiveOutage = false;
    @Column(name = "zone_or_node", length = 255)
    private String zoneOrNode;
    @Column(name = "eta_minutes")
    private Integer etaMinutes;
    @Column(name = "can_remote_reset", nullable = false)
    private boolean canRemoteReset = false;
    @Column(name = "incident_id", length = 64)
    private String incidentId;
    @Column(columnDefinition = "TEXT")
    private String message;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    

}
