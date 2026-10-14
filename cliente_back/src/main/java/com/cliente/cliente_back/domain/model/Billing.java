package com.cliente.cliente_back.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Billing {
    private Long id;
    private Long customerId;
    private String invoiceNumber;
    private BigDecimal totalAmount;
    private LocalDate dueDate;
    private LocalDate issueDate;
    private String periodLabel;
    private BillingStatus status;
    private BigDecimal previousBalance;
    private BigDecimal currentCharges;
    private BigDecimal discounts;
    private BigDecimal interests;

    public boolean isPaid() {
        return BillingStatus.PAID.equals(status);
    }
}

