package com.cliente.cliente_back.services.serviceImpl;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.cliente.cliente_back.dto.ConnectionResetRequestDTO;
import com.cliente.cliente_back.dto.ConnectionResetResponseDTO;
import com.cliente.cliente_back.dto.ResetJobStatus;
import com.cliente.cliente_back.entities.ConnectionResetEntity;
import com.cliente.cliente_back.entities.ServicesEntity;
import com.cliente.cliente_back.mappers.ConnectionResetMapper;
import com.cliente.cliente_back.repositories.ConnectionResetRepository;
import com.cliente.cliente_back.repositories.ServiceRepository;
import com.cliente.cliente_back.services.ConnectionResetService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ConnectionResetServiceImpl implements ConnectionResetService {

    private final ConnectionResetRepository connectionResetRepository;
    private final ConnectionResetMapper connectionResetMapper;
    private final ServiceRepository serviceRepository;

    @Override
    public ConnectionResetResponseDTO requestReset(ConnectionResetRequestDTO request) {
        if (request == null || request.customerId() == null || request.serviceId() == null) {
            throw new IllegalArgumentException("customerId y serviceId son obligatorios");
        }
        assertServiceBelongsToCustomer(request.customerId(), request.serviceId());

        ConnectionResetEntity entity = connectionResetMapper.fromRequest(request);
        entity.setResetJobId("RST-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        entity.setStatus(ResetJobStatus.QUEUED);
        entity.setEstimatedSeconds(120);
        entity.setMessage(
                "[TEST] Reset remoto simulado en cola. Reemplazar con integración OSS/CPE real.");
        entity.setCreatedAt(LocalDateTime.now());

        ConnectionResetEntity saved = connectionResetRepository.save(entity);
        return connectionResetMapper.toDto(saved);
    }

    private void assertServiceBelongsToCustomer(Long customerId, Long serviceId) {
        ServicesEntity service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new IllegalArgumentException("Servicio no encontrado"));
        long ownerId = service.getCustomerId().getId();
        if (ownerId != customerId) {
            throw new IllegalArgumentException("El servicio no pertenece al cliente indicado");
        }
    }
}
