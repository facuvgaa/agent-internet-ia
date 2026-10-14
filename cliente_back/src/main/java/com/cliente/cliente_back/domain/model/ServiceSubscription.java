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
public class ServiceSubscription {
    private Long id;
    private Long customerId;
    private String serviceName;
    private String serviceType;
    private String address;
    private BigDecimal basePrice;
    private BigDecimal discount;
    private BigDecimal discountPercentage;
    private LocalDate startDate;
    private Integer billingDay;
    private String status;
    private String promoExpiration;
    private LocalDate promoEndDate;

    public boolean belongsToCustomer(Long expectedCustomerId) {
        return this.customerId != null && this.customerId.equals(expectedCustomerId);
    }

    public boolean isActiveLine() {
        if (status == null || status.isBlank()) {
            return true;
        }
        String lower = status.toLowerCase();
        return !lower.contains("suspend") && !lower.contains("baja") && !lower.contains("cancel");
    }
}

