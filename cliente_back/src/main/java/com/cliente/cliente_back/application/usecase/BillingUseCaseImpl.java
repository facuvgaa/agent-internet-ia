package com.cliente.cliente_back.application.usecase;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import com.cliente.cliente_back.application.dto.BillingDTO;
import com.cliente.cliente_back.domain.model.Billing;
import com.cliente.cliente_back.domain.port.in.BillingUseCase;
import com.cliente.cliente_back.domain.port.out.BillingRepositoryPort;
import com.cliente.cliente_back.infrastructure.persistence.mapper.BillingEntityMapper;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BillingUseCaseImpl implements BillingUseCase {

    private final BillingRepositoryPort billingRepositoryPort;
    private final BillingEntityMapper billingMapper;

    @Override
    public List<BillingDTO> findAllBilling(Long customerId) {
        return billingRepositoryPort.findByCustomerId(customerId).stream()
                .map(billingMapper::toDto)
                .toList();
    }

    @Override
    public Optional<BillingDTO> findBillingByCustomerAndInvoiceNumber(Long customerId, String invoiceNumber) {
        if (customerId == null || invoiceNumber == null || invoiceNumber.isBlank()) {
            throw new IllegalArgumentException("customerId y invoiceNumber son obligatorios");
        }
        String trimmed = invoiceNumber.trim();

        // 1) Si es solo dígitos: id interno de facturación
        if (trimmed.matches("\\d+")) {
            try {
                long id = Long.parseLong(trimmed);
                Optional<Billing> byId = billingRepositoryPort.findByCustomerIdAndId(customerId, id);
                if (byId.isPresent()) {
                    return byId.map(billingMapper::toDto);
                }
            } catch (NumberFormatException ignored) {}
        }

        // 2) Número de factura impreso (columna invoice_number)
        Optional<Billing> byInvoice = billingRepositoryPort.findByCustomerIdAndInvoiceNumberIgnoreCase(customerId, trimmed);
        if (byInvoice.isPresent()) {
            return byInvoice.map(billingMapper::toDto);
        }

        // 3) Coincidencia con periodLabel
        return billingRepositoryPort.findByCustomerId(customerId).stream()
                .filter(b -> b.getPeriodLabel() != null && b.getPeriodLabel().equalsIgnoreCase(trimmed))
                .findFirst()
                .map(billingMapper::toDto);
    }
}
