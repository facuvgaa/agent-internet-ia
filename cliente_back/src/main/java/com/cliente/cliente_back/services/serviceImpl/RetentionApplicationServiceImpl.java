package com.cliente.cliente_back.services.serviceImpl;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cliente.cliente_back.dto.retention.RetentionApplicationRequestDTO;
import com.cliente.cliente_back.dto.retention.RetentionApplicationResponseDTO;
import com.cliente.cliente_back.entities.RetentionApplicationEntity;
import com.cliente.cliente_back.entities.ServicesEntity;
import com.cliente.cliente_back.repositories.CustomerRepository;
import com.cliente.cliente_back.repositories.RetentionApplicationRepository;
import com.cliente.cliente_back.repositories.ServiceRepository;
import com.cliente.cliente_back.retention.RetentionApplicationStatus;
import com.cliente.cliente_back.retention.RetentionTierSpec;
import com.cliente.cliente_back.services.RetentionApplicationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class RetentionApplicationServiceImpl implements RetentionApplicationService {

    private final RetentionApplicationRepository retentionApplicationRepository;
    private final CustomerRepository customerRepository;
    private final ServiceRepository serviceRepository;

    @Override
    @Transactional
    public RetentionApplicationResponseDTO apply(RetentionApplicationRequestDTO request) {
        if (request.customerId() == null || request.serviceId() == null) {
            throw new IllegalArgumentException("customerId y serviceId son obligatorios");
        }

        String idem = request.idempotencyKey();
        if (idem != null && !idem.isBlank()) {
            var existing = retentionApplicationRepository.findByIdempotencyKey(idem.trim());
            if (existing.isPresent()) {
                return toDto(
                        existing.get(),
                        "Registro idempotente: ya existía una aplicación con esta clave.");
            }
        }

        RetentionTierSpec tier =
                RetentionTierSpec.fromLevel(request.level())
                        .orElseThrow(() -> new IllegalArgumentException("Nivel inválido: use 1 a 4"));

        if (!customerRepository.existsById(request.customerId())) {
            throw new IllegalArgumentException("Cliente no encontrado");
        }

        ServicesEntity service =
                serviceRepository
                        .findById(request.serviceId())
                        .orElseThrow(() -> new IllegalArgumentException("Servicio no encontrado"));

        if (service.getCustomerId() == null
                || service.getCustomerId().getId() != request.customerId()) {
            throw new IllegalArgumentException("El servicio no pertenece al cliente");
        }

        LocalDateTime now = LocalDateTime.now();
        if (retentionApplicationRepository.existsByServiceIdAndStatusAndValidUntilAfter(
                request.serviceId(), RetentionApplicationStatus.APPLIED, now)) {
            throw new IllegalArgumentException(
                    "Ya existe una promoción de retención vigente para este servicio");
        }

        LocalDateTime validUntil = now.plusMonths(tier.durationMonths());

        RetentionApplicationEntity entity = new RetentionApplicationEntity();
        entity.setCustomerId(request.customerId());
        entity.setServiceId(request.serviceId());
        entity.setLevel(tier.level());
        entity.setDiscountPercent(tier.discountPercent());
        entity.setDurationMonths(tier.durationMonths());
        entity.setValidFrom(now);
        entity.setValidUntil(validUntil);
        if (idem != null && !idem.isBlank()) {
            entity.setIdempotencyKey(idem.trim());
        }
        entity.setChannel(request.channel());
        entity.setAcceptedTermsVersion(request.acceptedTermsVersion());
        entity.setNotes(request.notes());
        entity.setStatus(RetentionApplicationStatus.APPLIED);

        RetentionApplicationEntity saved = retentionApplicationRepository.save(entity);
        log.info(
                "Retention APPLIED id={} customerId={} serviceId={} level={} {}% {}meses hasta {}",
                saved.getId(),
                saved.getCustomerId(),
                saved.getServiceId(),
                saved.getLevel(),
                saved.getDiscountPercent(),
                saved.getDurationMonths(),
                saved.getValidUntil());

        return toDto(saved, "Promoción de retención registrada. La facturación puede leer retention_applications.");
    }

    private static RetentionApplicationResponseDTO toDto(
            RetentionApplicationEntity e, String message) {
        return new RetentionApplicationResponseDTO(
                e.getId(),
                e.getCustomerId(),
                e.getServiceId(),
                e.getLevel(),
                e.getDiscountPercent(),
                e.getDurationMonths(),
                e.getValidFrom().toString(),
                e.getValidUntil().toString(),
                e.getStatus().name(),
                message);
    }
}
