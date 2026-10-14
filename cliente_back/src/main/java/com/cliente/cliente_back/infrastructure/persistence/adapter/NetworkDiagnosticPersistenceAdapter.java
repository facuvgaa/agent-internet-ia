package com.cliente.cliente_back.infrastructure.persistence.adapter;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import com.cliente.cliente_back.domain.model.NetworkDiagnostic;
import com.cliente.cliente_back.domain.port.out.NetworkDiagnosticRepositoryPort;
import com.cliente.cliente_back.infrastructure.persistence.mapper.NetworkDiagnosticEntityMapper;
import com.cliente.cliente_back.infrastructure.persistence.repository.NetworkDiagnosticRepository;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class NetworkDiagnosticPersistenceAdapter implements NetworkDiagnosticRepositoryPort {

    private final NetworkDiagnosticRepository networkDiagnosticRepository;
    private final NetworkDiagnosticEntityMapper networkDiagnosticMapper;

    @Override
    public NetworkDiagnostic save(NetworkDiagnostic diagnostic) {
        var entity = networkDiagnosticMapper.toEntity(diagnostic);
        var saved = networkDiagnosticRepository.save(entity);
        return networkDiagnosticMapper.toDomain(saved);
    }

    @Override
    public List<NetworkDiagnostic> findByCustomerIdAndServiceIdOrderByCreatedAtDesc(Long customerId, Long serviceId) {
        return networkDiagnosticRepository.findByCustomerIdAndServiceIdOrderByCreatedAtDesc(customerId, serviceId)
                .stream()
                .map(networkDiagnosticMapper::toDomain)
                .toList();
    }

    @Override
    public Optional<NetworkDiagnostic> findFirstByCustomerIdAndServiceIdOrderByCreatedAtDesc(Long customerId, Long serviceId) {
        return networkDiagnosticRepository.findFirstByCustomerIdAndServiceIdOrderByCreatedAtDesc(customerId, serviceId)
                .map(networkDiagnosticMapper::toDomain);
    }
}
