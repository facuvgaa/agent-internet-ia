package com.cliente.cliente_back.application.usecase;

import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import com.cliente.cliente_back.application.dto.ConnectionResetRequestDTO;
import com.cliente.cliente_back.application.dto.ConnectionResetResponseDTO;
import com.cliente.cliente_back.domain.model.ConnectionReset;
import com.cliente.cliente_back.domain.model.ResetJobStatus;
import com.cliente.cliente_back.domain.model.ServiceSubscription;
import com.cliente.cliente_back.domain.port.in.ConnectionResetUseCase;
import com.cliente.cliente_back.domain.port.out.ConnectionResetRepositoryPort;
import com.cliente.cliente_back.domain.port.out.ServiceRepositoryPort;
import com.cliente.cliente_back.domain.service.DomainOwnershipValidator;
import com.cliente.cliente_back.infrastructure.persistence.mapper.ConnectionResetEntityMapper;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ConnectionResetUseCaseImpl implements ConnectionResetUseCase {

    private final ConnectionResetRepositoryPort connectionResetRepositoryPort;
    private final ConnectionResetEntityMapper connectionResetMapper;
    private final ServiceRepositoryPort serviceRepositoryPort;

    @Override
    public ConnectionResetResponseDTO requestReset(ConnectionResetRequestDTO request) {
        if (request == null || request.customerId() == null || request.serviceId() == null) {
            throw new IllegalArgumentException("customerId y serviceId son obligatorios");
        }
        ServiceSubscription service = serviceRepositoryPort.findById(request.serviceId())
                .orElseThrow(() -> new IllegalArgumentException("Servicio no encontrado"));
        DomainOwnershipValidator.assertServiceBelongsToCustomer(service, request.customerId());

        ConnectionReset reset = ConnectionReset.builder()
                .resetJobId("RST-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12))
                .customerId(request.customerId())
                .serviceId(request.serviceId())
                .reason(request.reason())
                .status(ResetJobStatus.QUEUED)
                .estimatedSeconds(120)
                .message("[TEST] Reset remoto simulado en cola. Reemplazar con integración OSS/CPE real.")
                .createdAt(LocalDateTime.now())
                .build();

        ConnectionReset saved = connectionResetRepositoryPort.save(reset);
        return connectionResetMapper.toDto(saved);
    }
}
