package com.cliente.cliente_back.entities;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.cliente.cliente_back.dto.BillingStatus;

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
@Table(name="facturacion")
public class BillingEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    private long customerId;

    /**
     * Número impreso en la factura (PDF/captura). Si es null, se puede buscar por id interno o periodLabel.
     */
    @Column(name = "invoice_number", length = 64)
    private String invoiceNumber;

    private BigDecimal totalAmount; 
    private LocalDate dueDate;  
    private LocalDate issueDate;     
    private String periodLabel; 
    @Enumerated(EnumType.STRING)    
    private BillingStatus status;    
    private BigDecimal previousBalance;    
    private BigDecimal currentCharges;    
    private BigDecimal discounts;          
    private BigDecimal interests;          
}
