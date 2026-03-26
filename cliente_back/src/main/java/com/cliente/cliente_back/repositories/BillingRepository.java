package com.cliente.cliente_back.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cliente.cliente_back.entities.BillingEntity;

public interface BillingRepository extends JpaRepository<BillingEntity, Long> {

    List<BillingEntity> findByCustomerId(String customerId);

    Optional<BillingEntity> findByCustomerIdAndId(String customerId, long id);

    Optional<BillingEntity> findByCustomerIdAndInvoiceNumberIgnoreCase(String customerId, String invoiceNumber);
}
