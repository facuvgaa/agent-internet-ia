package com.cliente.cliente_back.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cliente.cliente_back.entities.NetworkDiagnosticEntity;

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
