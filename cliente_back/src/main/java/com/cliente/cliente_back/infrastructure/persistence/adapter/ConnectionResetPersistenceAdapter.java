package com.cliente.cliente_back.infrastructure.persistence.adapter;

import java.util.Optional;
import org.springframework.stereotype.Component;
import com.cliente.cliente_back.domain.model.ConnectionReset;
import com.cliente.cliente_back.domain.port.out.ConnectionResetRepositoryPort;
import com.cliente.cliente_back.infrastructure.persistence.mapper.ConnectionResetEntityMapper;
import com.cliente.cliente_back.infrastructure.persistence.repository.ConnectionResetRepository;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ConnectionResetPersistenceAdapter implements ConnectionResetRepositoryPort {

    private final ConnectionResetRepository connectionResetRepository;
    private final ConnectionResetEntityMapper connectionResetMapper;

    @Override
    public ConnectionReset save(ConnectionReset reset) {
        var entity = connectionResetMapper.toEntity(reset);
        var saved = connectionResetRepository.save(entity);
        return connectionResetMapper.toDomain(saved);
    }

    @Override
    public Optional<ConnectionReset> findById(Long id) {
        return connectionResetRepository.findById(id).map(connectionResetMapper::toDomain);
    }
}

