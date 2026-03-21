package com.cliente.cliente_back.services;

import java.util.List;
import java.util.Optional;

import com.cliente.cliente_back.dto.NetworkDiagnosticRequestDTO;
import com.cliente.cliente_back.dto.NetworkDiagnosticResponseDTO;

public interface NetworkDiagnosticService {

    
    NetworkDiagnosticResponseDTO runDiagnostic(NetworkDiagnosticRequestDTO request);

    List<NetworkDiagnosticResponseDTO> findByCustomerIdAndServiceIdOrderByCreatedAtDesc(
        Long customerId,
        Long serviceId
    );
    Optional<NetworkDiagnosticResponseDTO> findFirstByCustomerIdAndServiceIdOrderByCreatedAtDesc(
        Long customerId,
        Long serviceId
    );
}
