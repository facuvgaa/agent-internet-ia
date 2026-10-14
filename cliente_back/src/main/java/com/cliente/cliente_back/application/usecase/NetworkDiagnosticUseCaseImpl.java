package com.cliente.cliente_back.application.usecase;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import com.cliente.cliente_back.application.dto.NetworkDiagnosticRequestDTO;
import com.cliente.cliente_back.application.dto.NetworkDiagnosticResponseDTO;
import com.cliente.cliente_back.domain.model.NetworkDiagnostic;
import com.cliente.cliente_back.domain.model.NetworkHealthStatus;
import com.cliente.cliente_back.domain.model.ServiceSubscription;
import com.cliente.cliente_back.domain.port.in.NetworkDiagnosticUseCase;
import com.cliente.cliente_back.domain.port.out.NetworkDiagnosticRepositoryPort;
import com.cliente.cliente_back.domain.port.out.ServiceRepositoryPort;
import com.cliente.cliente_back.domain.service.DomainOwnershipValidator;
import com.cliente.cliente_back.infrastructure.persistence.mapper.NetworkDiagnosticEntityMapper;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NetworkDiagnosticUseCaseImpl implements NetworkDiagnosticUseCase {

    private final NetworkDiagnosticRepositoryPort networkDiagnosticRepositoryPort;
    private final NetworkDiagnosticEntityMapper networkDiagnosticMapper;
    private final ServiceRepositoryPort serviceRepositoryPort;

    @Override
    public NetworkDiagnosticResponseDTO runDiagnostic(NetworkDiagnosticRequestDTO request) {
        if (request == null || request.customerId() == null || request.serviceId() == null) {
            throw new IllegalArgumentException("customerId y serviceId son obligatorios");
        }
        ServiceSubscription service = serviceRepositoryPort.findById(request.serviceId())
                .orElseThrow(() -> new IllegalArgumentException("Servicio no encontrado"));
        DomainOwnershipValidator.assertServiceBelongsToCustomer(service, request.customerId());

        NetworkDiagnostic diagnostic = NetworkDiagnostic.builder()
                .customerId(request.customerId())
                .serviceId(request.serviceId())
                .channel(request.channel())
                .status(NetworkHealthStatus.OK)
                .massiveOutage(false)
                .zoneOrNode("stub-lab-node")
                .etaMinutes(null)
                .canRemoteReset(true)
                .incidentId(null)
                .message("[TEST] Diagnóstico simulado: línea OK. Cuando integres red real, acá va el resultado verdadero.")
                .createdAt(LocalDateTime.now())
                .build();

        NetworkDiagnostic saved = networkDiagnosticRepositoryPort.save(diagnostic);
        return networkDiagnosticMapper.toDto(saved);
    }

    @Override
    public List<NetworkDiagnosticResponseDTO> findByCustomerIdAndServiceIdOrderByCreatedAtDesc(Long customerId, Long serviceId) {
        ServiceSubscription service = serviceRepositoryPort.findById(serviceId)
                .orElseThrow(() -> new IllegalArgumentException("Servicio no encontrado"));
        DomainOwnershipValidator.assertServiceBelongsToCustomer(service, customerId);

        return networkDiagnosticRepositoryPort.findByCustomerIdAndServiceIdOrderByCreatedAtDesc(customerId, serviceId).stream()
                .map(networkDiagnosticMapper::toDto)
                .toList();
    }

    @Override
    public Optional<NetworkDiagnosticResponseDTO> findFirstByCustomerIdAndServiceIdOrderByCreatedAtDesc(Long customerId, Long serviceId) {
        ServiceSubscription service = serviceRepositoryPort.findById(serviceId)
                .orElseThrow(() -> new IllegalArgumentException("Servicio no encontrado"));
        DomainOwnershipValidator.assertServiceBelongsToCustomer(service, customerId);

        return networkDiagnosticRepositoryPort.findFirstByCustomerIdAndServiceIdOrderByCreatedAtDesc(customerId, serviceId)
                .map(networkDiagnosticMapper::toDto);
    }
}

