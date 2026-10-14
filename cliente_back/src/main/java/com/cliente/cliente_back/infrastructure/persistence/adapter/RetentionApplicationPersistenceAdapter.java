package com.cliente.cliente_back.infrastructure.persistence.adapter;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Component;
import com.cliente.cliente_back.domain.model.RetentionApplication;
import com.cliente.cliente_back.domain.model.RetentionApplicationStatus;
import com.cliente.cliente_back.domain.port.out.RetentionApplicationRepositoryPort;
import com.cliente.cliente_back.infrastructure.persistence.mapper.RetentionApplicationEntityMapper;
import com.cliente.cliente_back.infrastructure.persistence.repository.RetentionApplicationRepository;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RetentionApplicationPersistenceAdapter implements RetentionApplicationRepositoryPort {

    private final RetentionApplicationRepository retentionApplicationRepository;
    private final RetentionApplicationEntityMapper retentionApplicationMapper;

    @Override
    public RetentionApplication save(RetentionApplication application) {
        var entity = retentionApplicationMapper.toEntity(application);
        var saved = retentionApplicationRepository.save(entity);
        return retentionApplicationMapper.toDomain(saved);
    }

    @Override
    public Optional<RetentionApplication> findByIdempotencyKey(String idempotencyKey) {
        return retentionApplicationRepository.findByIdempotencyKey(idempotencyKey)
                .map(retentionApplicationMapper::toDomain);
    }

    @Override
    public boolean existsByServiceIdAndStatusAndValidUntilAfter(Long serviceId, RetentionApplicationStatus status, LocalDateTime now) {
        return retentionApplicationRepository.existsByServiceIdAndStatusAndValidUntilAfter(serviceId, status, now);
    }
}

