package com.cliente.cliente_back.application.dto;

import com.cliente.cliente_back.domain.model.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record BillingDTO(
    Long id,
    Long customerId,
    String invoiceNumber,
    BigDecimal totalAmount,  
    LocalDate dueDate,       
    LocalDate issueDate,     
    String periodLabel,      
    BillingStatus status,    
    BigDecimal previousBalance,    
    BigDecimal currentCharges,     
    BigDecimal discounts,          
    BigDecimal interests,          
    List<String> serviceSummary
) {}