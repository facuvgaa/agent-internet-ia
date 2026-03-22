package com.cliente.cliente_back.repositories;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cliente.cliente_back.entities.RetentionApplicationEntity;
import com.cliente.cliente_back.retention.RetentionApplicationStatus;

public interface RetentionApplicationRepository extends JpaRepository<RetentionApplicationEntity, Long> {

    Optional<RetentionApplicationEntity> findByIdempotencyKey(String idempotencyKey);

    boolean existsByServiceIdAndStatusAndValidUntilAfter(
            Long serviceId, RetentionApplicationStatus status, LocalDateTime instant);
}
