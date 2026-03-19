package com.cliente.cliente_back.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cliente.cliente_back.entities.PaymentPromiseEntity;

public interface PaymentPromiseRepository extends JpaRepository<PaymentPromiseEntity, Long> {
}
