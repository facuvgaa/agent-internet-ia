package com.cliente.cliente_back.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ServiceEntity(
    Long id, 
    String serviceName,
    String serviceType,
    String address,
    BigDecimal basePrice,
    BigDecimal discount,
    LocalDate startDate,
    Integer billingDay,
    String status,
    String promoExpiration
){}
