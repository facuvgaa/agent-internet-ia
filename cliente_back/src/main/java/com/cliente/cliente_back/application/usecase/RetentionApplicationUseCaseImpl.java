package com.cliente.cliente_back.application.usecase;

import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.cliente.cliente_back.application.dto.retention.RetentionApplicationRequestDTO;
import com.cliente.cliente_back.application.dto.retention.RetentionApplicationResponseDTO;
import com.cliente.cliente_back.domain.model.RetentionApplication;
import com.cliente.cliente_back.domain.model.RetentionApplicationStatus;
import com.cliente.cliente_back.domain.model.RetentionTierSpec;
import com.cliente.cliente_back.domain.model.ServiceSubscription;
import com.cliente.cliente_back.domain.port.in.RetentionApplicationUseCase;
import com.cliente.cliente_back.domain.port.out.CustomerRepositoryPort;
import com.cliente.cliente_back.domain.port.out.RetentionApplicationRepositoryPort;
import com.cliente.cliente_back.domain.port.out.ServiceRepositoryPort;
import com.cliente.cliente_back.domain.service.DomainOwnershipValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class RetentionApplicationUseCaseImpl implements RetentionApplicationUseCase {

    private final RetentionApplicationRepositoryPort retentionApplicationRepositoryPort;
    private final CustomerRepositoryPort customerRepositoryPort;
    private final ServiceRepositoryPort serviceRepositoryPort;

    @Override
    @Transactional
    public RetentionApplicationResponseDTO apply(RetentionApplicationRequestDTO request) {
        if (request.customerId() == null || request.serviceId() == null) {
            throw new IllegalArgumentException("customerId y serviceId son obligatorios");
        }

        String idem = request.idempotencyKey();
        if (idem != null && !idem.isBlank()) {
            var existing = retentionApplicationRepositoryPort.findByIdempotencyKey(idem.trim());
            if (existing.isPresent()) {
                return toDto(existing.get(), "Registro idempotente: ya existía una aplicación con esta clave.");
            }
        }

        RetentionTierSpec tier = RetentionTierSpec.fromLevel(request.level())
                .orElseThrow(() -> new IllegalArgumentException("Nivel inválido: use 1 a 4"));

        if (!customerRepositoryPort.existsById(request.customerId())) {
            throw new IllegalArgumentException("Cliente no encontrado");
        }

        ServiceSubscription service = serviceRepositoryPort.findById(request.serviceId())
                .orElseThrow(() -> new IllegalArgumentException("Servicio no encontrado"));

        DomainOwnershipValidator.assertServiceBelongsToCustomer(service, request.customerId());

        LocalDateTime now = LocalDateTime.now();
        if (retentionApplicationRepositoryPort.existsByServiceIdAndStatusAndValidUntilAfter(
                request.serviceId(), RetentionApplicationStatus.APPLIED, now)) {
            throw new IllegalArgumentException("Ya existe una promoción de retención vigente para este servicio");
        }

        LocalDateTime validUntil = now.plusMonths(tier.durationMonths());

        RetentionApplication app = RetentionApplication.builder()
                .customerId(request.customerId())
                .serviceId(request.serviceId())
                .level(tier.level())
                .discountPercent(tier.discountPercent())
                .durationMonths(tier.durationMonths())
                .validFrom(now)
                .validUntil(validUntil)
                .idempotencyKey(idem != null && !idem.isBlank() ? idem.trim() : null)
                .channel(request.channel())
                .acceptedTermsVersion(request.acceptedTermsVersion())
                .notes(request.notes())
                .status(RetentionApplicationStatus.APPLIED)
                .createdAt(now)
                .build();

        RetentionApplication saved = retentionApplicationRepositoryPort.save(app);
        log.info("Retention APPLIED id={} customerId={} serviceId={} level={} {}% {}meses hasta {}",
                saved.getId(), saved.getCustomerId(), saved.getServiceId(), saved.getLevel(),
                saved.getDiscountPercent(), saved.getDurationMonths(), saved.getValidUntil());

        return toDto(saved, "Acuerdo de retención registrado con éxito");
    }

    private RetentionApplicationResponseDTO toDto(RetentionApplication app, String message) {
        return new RetentionApplicationResponseDTO(
                app.getId(),
                app.getCustomerId(),
                app.getServiceId(),
                app.getLevel(),
                app.getDiscountPercent(),
                app.getDurationMonths(),
                app.getValidFrom() == null ? null : app.getValidFrom().toString(),
                app.getValidUntil() == null ? null : app.getValidUntil().toString(),
                app.getStatus() == null ? null : app.getStatus().name(),
                message);
    }
}

