package com.cliente.cliente_back.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cliente.cliente_back.entities.BillingEntity;

public interface BillingRepository extends JpaRepository<BillingEntity, Long> {

    List<BillingEntity> findByCustomerId(Long customerId);

    Optional<BillingEntity> findByCustomerIdAndId(long customerId, long id);

    Optional<BillingEntity> findByCustomerIdAndInvoiceNumberIgnoreCase(long customerId, String invoiceNumber);
}
