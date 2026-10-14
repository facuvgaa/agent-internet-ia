package com.cliente.cliente_back.domain.port.in;

import java.util.List;
import java.util.Optional;
import com.cliente.cliente_back.application.dto.NetworkDiagnosticRequestDTO;
import com.cliente.cliente_back.application.dto.NetworkDiagnosticResponseDTO;

public interface NetworkDiagnosticUseCase {
    NetworkDiagnosticResponseDTO runDiagnostic(NetworkDiagnosticRequestDTO request);
    List<NetworkDiagnosticResponseDTO> findByCustomerIdAndServiceIdOrderByCreatedAtDesc(Long customerId, Long serviceId);
    Optional<NetworkDiagnosticResponseDTO> findFirstByCustomerIdAndServiceIdOrderByCreatedAtDesc(Long customerId, Long serviceId);
}

