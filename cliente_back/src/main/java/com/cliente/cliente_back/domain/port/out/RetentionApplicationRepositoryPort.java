package com.cliente.cliente_back.domain.port.out;

import java.time.LocalDateTime;
import java.util.Optional;
import com.cliente.cliente_back.domain.model.RetentionApplication;
import com.cliente.cliente_back.domain.model.RetentionApplicationStatus;

public interface RetentionApplicationRepositoryPort {
    RetentionApplication save(RetentionApplication application);
    Optional<RetentionApplication> findByIdempotencyKey(String idempotencyKey);
    boolean existsByServiceIdAndStatusAndValidUntilAfter(Long serviceId, RetentionApplicationStatus status, LocalDateTime now);
}
