package com.cliente.cliente_back.services.serviceImpl;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.cliente.cliente_back.dto.BillingDTO;
import com.cliente.cliente_back.entities.BillingEntity;
import com.cliente.cliente_back.mappers.BillingMapper;
import com.cliente.cliente_back.repositories.BillingRepository;
import com.cliente.cliente_back.services.BillingService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BillingServiceImpl implements BillingService {

    private final BillingRepository billingRepository;
    private final BillingMapper billingMapper;

    private static String customerIdToKey(Long customerId) {
        return customerId == null ? null : String.valueOf(customerId);
    }

    @Override
    public List<BillingDTO> findAllBilling(Long id) {
        return billingRepository.findByCustomerId(customerIdToKey(id)).stream()
                .map(billingMapper::toDto)
                .toList();
    }

    @Override
    public Optional<BillingDTO> findBillingByCustomerAndInvoiceNumber(
            Long customerId, String invoiceNumber) {
        if (customerId == null || invoiceNumber == null || invoiceNumber.isBlank()) {
            throw new IllegalArgumentException("customerId y invoiceNumber son obligatorios");
        }
        String trimmed = invoiceNumber.trim();
        String customerKey = customerIdToKey(customerId);

        // 1) Si es solo dígitos: id interno de facturación
        if (trimmed.matches("\\d+")) {
            try {
                long id = Long.parseLong(trimmed);
                Optional<BillingEntity> byId = billingRepository.findByCustomerIdAndId(customerKey, id);
                if (byId.isPresent()) {
                    return byId.map(billingMapper::toDto);
                }
            } catch (NumberFormatException ignored) {
                // seguir con otros criterios
            }
        }

        // 2) Número de factura impreso (columna invoice_number)
        Optional<BillingEntity> byInvoice =
                billingRepository.findByCustomerIdAndInvoiceNumberIgnoreCase(customerKey, trimmed);
        if (byInvoice.isPresent()) {
            return byInvoice.map(billingMapper::toDto);
        }

        // 3) Coincidencia con periodLabel (datos viejos sin invoice_number)
        return billingRepository.findByCustomerId(customerKey).stream()
                .filter(b -> b.getPeriodLabel() != null && b.getPeriodLabel().equalsIgnoreCase(trimmed))
                .findFirst()
                .map(billingMapper::toDto);
    }
}
