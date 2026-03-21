package com.cliente.cliente_back.services.serviceImpl;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.cliente.cliente_back.dto.NetworkDiagnosticResponseDTO;
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
