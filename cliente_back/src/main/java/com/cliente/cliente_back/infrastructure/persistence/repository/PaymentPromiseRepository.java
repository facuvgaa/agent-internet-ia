package com.cliente.cliente_back.infrastructure.persistence.repository;

import com.cliente.cliente_back.infrastructure.persistence.entity.*;
import com.cliente.cliente_back.domain.model.*;

import org.springframework.data.jpa.repository.JpaRepository;



public interface PaymentPromiseRepository extends JpaRepository<PaymentPromiseEntity, Long> {
}
