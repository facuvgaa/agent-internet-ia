package com.cliente.cliente_back.services;

import java.util.List;
import java.util.Optional;

import com.cliente.cliente_back.dto.BillingDTO;

public interface BillingService {
    List<BillingDTO> findAllBilling(Long customerId);

    /**
     * Busca una factura del cliente por número (id interno, {@code invoice_number} o etiqueta de período).
     * El texto puede venir de OCR/LLM a partir de una foto de la factura.
     */
    Optional<BillingDTO> findBillingByCustomerAndInvoiceNumber(Long customerId, String invoiceNumber);
}
