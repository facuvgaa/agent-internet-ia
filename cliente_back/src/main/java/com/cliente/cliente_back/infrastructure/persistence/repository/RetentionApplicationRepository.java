package com.cliente.cliente_back.infrastructure.persistence.repository;

import com.cliente.cliente_back.infrastructure.persistence.entity.*;
import com.cliente.cliente_back.domain.model.*;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;




public interface RetentionApplicationRepository extends JpaRepository<RetentionApplicationEntity, Long> {

    Optional<RetentionApplicationEntity> findByIdempotencyKey(String idempotencyKey);

    boolean existsByServiceIdAndStatusAndValidUntilAfter(
            Long serviceId, RetentionApplicationStatus status, LocalDateTime instant);
}
