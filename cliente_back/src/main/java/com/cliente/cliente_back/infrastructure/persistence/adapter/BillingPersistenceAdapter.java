package com.cliente.cliente_back.infrastructure.persistence.adapter;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import com.cliente.cliente_back.domain.model.Billing;
import com.cliente.cliente_back.domain.port.out.BillingRepositoryPort;
import com.cliente.cliente_back.infrastructure.persistence.mapper.BillingEntityMapper;
import com.cliente.cliente_back.infrastructure.persistence.repository.BillingRepository;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class BillingPersistenceAdapter implements BillingRepositoryPort {

    private final BillingRepository billingRepository;
    private final BillingEntityMapper billingMapper;

    private static String customerIdToKey(Long customerId) {
        return customerId == null ? null : String.valueOf(customerId);
    }

    @Override
    public List<Billing> findByCustomerId(Long customerId) {
        return billingRepository.findByCustomerId(customerIdToKey(customerId)).stream()
                .map(billingMapper::toDomain)
                .toList();
    }

    @Override
    public Optional<Billing> findById(Long id) {
        return billingRepository.findById(id).map(billingMapper::toDomain);
    }

    @Override
    public Optional<Billing> findByCustomerIdAndId(Long customerId, Long id) {
        return billingRepository.findByCustomerIdAndId(customerIdToKey(customerId), id)
                .map(billingMapper::toDomain);
    }

    @Override
    public Optional<Billing> findByCustomerIdAndInvoiceNumberIgnoreCase(Long customerId, String invoiceNumber) {
        return billingRepository.findByCustomerIdAndInvoiceNumberIgnoreCase(customerIdToKey(customerId), invoiceNumber)
                .map(billingMapper::toDomain);
    }

    @Override
    public Billing save(Billing billing) {
        var entity = billingMapper.toEntity(billing);
        var saved = billingRepository.save(entity);
        return billingMapper.toDomain(saved);
    }
}
