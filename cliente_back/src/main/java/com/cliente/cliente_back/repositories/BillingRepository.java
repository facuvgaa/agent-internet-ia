package com.cliente.cliente_back.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cliente.cliente_back.entities.BillingEntity;


public interface BillingRepository extends JpaRepository<BillingEntity, Long>{
    List<BillingEntity> findByCustomerId(Long customerId);
}
