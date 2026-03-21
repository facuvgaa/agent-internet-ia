package com.cliente.cliente_back.services.serviceImpl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.cliente.cliente_back.dto.NetworkDiagnosticRequestDTO;
import com.cliente.cliente_back.dto.NetworkDiagnosticResponseDTO;
import com.cliente.cliente_back.dto.NetworkHealthStatus;
import com.cliente.cliente_back.entities.NetworkDiagnosticEntity;
import com.cliente.cliente_back.entities.ServicesEntity;
import com.cliente.cliente_back.mappers.NetworkDiagnosticMapper;
import com.cliente.cliente_back.repositories.NetworkDiagnosticRepository;
import com.cliente.cliente_back.repositories.ServiceRepository;
import com.cliente.cliente_back.services.NetworkDiagnosticService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NetworkDiagnosticServiceImpl implements NetworkDiagnosticService {

    private final NetworkDiagnosticRepository networkDiagnosticRepository;
    private final NetworkDiagnosticMapper networkDiagnosticMapper;
    private final ServiceRepository serviceRepository;

    @Override
    public NetworkDiagnosticResponseDTO runDiagnostic(NetworkDiagnosticRequestDTO request) {
        if (request == null || request.customerId() == null || request.serviceId() == null) {
            throw new IllegalArgumentException("customerId y serviceId son obligatorios");
        }
        assertServiceBelongsToCustomer(request.customerId(), request.serviceId());

        NetworkDiagnosticEntity entity = networkDiagnosticMapper.fromRequest(request);
        // --- Stub de laboratorio: nadie usa esto en prod todavía ---
        entity.setStatus(NetworkHealthStatus.OK);
        entity.setMassiveOutage(false);
        entity.setZoneOrNode("stub-lab-node");
        entity.setEtaMinutes(null);
        entity.setCanRemoteReset(true);
        entity.setIncidentId(null);
        entity.setMessage(
                "[TEST] Diagnóstico simulado: línea OK. Cuando integres red real, acá va el resultado verdadero.");
        entity.setCreatedAt(LocalDateTime.now());

        NetworkDiagnosticEntity saved = networkDiagnosticRepository.save(entity);
        return networkDiagnosticMapper.toDto(saved);
    }

    @Override
    public List<NetworkDiagnosticResponseDTO> findByCustomerIdAndServiceIdOrderByCreatedAtDesc(
            Long customerId,
            Long serviceId) {
        assertServiceBelongsToCustomer(customerId, serviceId);
        return networkDiagnosticRepository
                .findByCustomerIdAndServiceIdOrderByCreatedAtDesc(customerId, serviceId)
                .stream()
                .map(networkDiagnosticMapper::toDto)
                .toList();
    }

    @Override
    public Optional<NetworkDiagnosticResponseDTO> findFirstByCustomerIdAndServiceIdOrderByCreatedAtDesc(
            Long customerId,
            Long serviceId) {
        assertServiceBelongsToCustomer(customerId, serviceId);
        return networkDiagnosticRepository
                .findFirstByCustomerIdAndServiceIdOrderByCreatedAtDesc(customerId, serviceId)
                .map(networkDiagnosticMapper::toDto);
    }

    private void assertServiceBelongsToCustomer(Long customerId, Long serviceId) {
        if (customerId == null || serviceId == null) {
            throw new IllegalArgumentException("customerId y serviceId son obligatorios");
        }
        ServicesEntity service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new IllegalArgumentException("Servicio no encontrado"));
        long ownerId = service.getCustomerId().getId();
        if (ownerId != customerId) {
            throw new IllegalArgumentException("El servicio no pertenece al cliente indicado");
        }
    }
}
