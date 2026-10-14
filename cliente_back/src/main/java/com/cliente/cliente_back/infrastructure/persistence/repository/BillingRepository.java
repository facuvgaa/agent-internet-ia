package com.cliente.cliente_back.infrastructure.persistence.repository;

import com.cliente.cliente_back.infrastructure.persistence.entity.*;
import com.cliente.cliente_back.domain.model.*;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;



public interface BillingRepository extends JpaRepository<BillingEntity, Long> {

    List<BillingEntity> findByCustomerId(String customerId);

    Optional<BillingEntity> findByCustomerIdAndId(String customerId, long id);

    Optional<BillingEntity> findByCustomerIdAndInvoiceNumberIgnoreCase(String customerId, String invoiceNumber);
}
