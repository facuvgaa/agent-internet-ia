package com.cliente.cliente_back.domain.port.out;

import java.util.List;
import java.util.Optional;
import com.cliente.cliente_back.domain.model.Billing;

public interface BillingRepositoryPort {
    List<Billing> findByCustomerId(Long customerId);
    Optional<Billing> findById(Long id);
    Optional<Billing> findByCustomerIdAndId(Long customerId, Long id);
    Optional<Billing> findByCustomerIdAndInvoiceNumberIgnoreCase(Long customerId, String invoiceNumber);
    Billing save(Billing billing);
}
