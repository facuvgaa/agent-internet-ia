package com.cliente.cliente_back.domain.port.in;

import java.util.List;
import java.util.Optional;
import com.cliente.cliente_back.application.dto.BillingDTO;

public interface BillingUseCase {
    List<BillingDTO> findAllBilling(Long customerId);
    Optional<BillingDTO> findBillingByCustomerAndInvoiceNumber(Long customerId, String invoiceNumber);
}

