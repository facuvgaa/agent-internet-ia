package com.cliente.cliente_back.application.dto;

import com.cliente.cliente_back.domain.model.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ServicesDTO(
    Long id, 
    String serviceName,      
    String serviceType,      
    String address,         
    BigDecimal basePrice,   
    BigDecimal discount,     
    BigDecimal discountPercentage, 
    LocalDate startDate,    
    Integer billingDay,      
    String status,          
    String promoExpiration,  
    LocalDate promoEndDate   
){}