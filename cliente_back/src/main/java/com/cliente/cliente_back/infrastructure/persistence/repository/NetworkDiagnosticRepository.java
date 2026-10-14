package com.cliente.cliente_back.infrastructure.persistence.repository;

import com.cliente.cliente_back.infrastructure.persistence.entity.*;
import com.cliente.cliente_back.domain.model.*;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;



public interface NetworkDiagnosticRepository extends JpaRepository< NetworkDiagnosticEntity, Long > {
    List<NetworkDiagnosticEntity> findByCustomerIdAndServiceIdOrderByCreatedAtDesc(
        Long customerId,
        Long serviceId
    );
    Optional<NetworkDiagnosticEntity> findFirstByCustomerIdAndServiceIdOrderByCreatedAtDesc(
        Long customerId,
        Long serviceId
    );
}
