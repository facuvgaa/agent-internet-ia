package com.cliente.cliente_back.domain.port.out;

import java.util.List;
import java.util.Optional;
import com.cliente.cliente_back.domain.model.NetworkDiagnostic;

public interface NetworkDiagnosticRepositoryPort {
    NetworkDiagnostic save(NetworkDiagnostic diagnostic);
    List<NetworkDiagnostic> findByCustomerIdAndServiceIdOrderByCreatedAtDesc(Long customerId, Long serviceId);
    Optional<NetworkDiagnostic> findFirstByCustomerIdAndServiceIdOrderByCreatedAtDesc(Long customerId, Long serviceId);
}

