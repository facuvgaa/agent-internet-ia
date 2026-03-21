package com.cliente.cliente_back.entities;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "services")
@Data
public class ServicesEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private CustomerEntity customerId;

    @Column(nullable = false)
    private String serviceName;

    @Column(nullable = false)
    private String serviceType;


    @Column(nullable = false)
    private String address;

    private BigDecimal basePrice;

    private BigDecimal discount;

    private BigDecimal discountPercentage;

    private LocalDate startDate;

    private Integer billingDay;

    private String status;

    private String promoExpiration;

    private LocalDate promoEndDate;
}
