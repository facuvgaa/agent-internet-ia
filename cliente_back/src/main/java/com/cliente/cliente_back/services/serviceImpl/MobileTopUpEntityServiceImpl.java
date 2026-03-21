package com.cliente.cliente_back.services.serviceImpl;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cliente.cliente_back.dto.BenefitStatus;
import com.cliente.cliente_back.dto.MobileTopUpRequestDTO;
import com.cliente.cliente_back.dto.MobileTopUpResponseDTO;
import com.cliente.cliente_back.entities.CustomerEntity;
import com.cliente.cliente_back.entities.MobileTopUpEntity;
import com.cliente.cliente_back.mappers.MobileTopUpEntityMapper;
import com.cliente.cliente_back.repositories.CustomerRepository;
import com.cliente.cliente_back.repositories.MobileTopUpEntityRepository;
import com.cliente.cliente_back.repositories.TicketRequestRepository;
import com.cliente.cliente_back.services.MobileTopUpEntityService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class MobileTopUpEntityServiceImpl implements MobileTopUpEntityService {

    /** Días hasta que puede pedir otra recarga en la misma línea (mismo cliente + msisdn). */
    private static final int COOLDOWN_DAYS = 30;

    /** Vigencia informativa del paquete de datos (para mostrar al usuario / auditoría). */
    private static final int PACKAGE_VALIDITY_DAYS = 30;

    private final MobileTopUpEntityRepository mobileTopUpEntityRepository;
    private final MobileTopUpEntityMapper mobileTopUpEntityMapper;
    private final TicketRequestRepository ticketRequestRepository;
    private final CustomerRepository customerRepository;

    @Override
    @Transactional
    public MobileTopUpResponseDTO grantTopUp(MobileTopUpRequestDTO request) {
        requireNonNullIds(request);
        String msisdnNorm = normalizeMsisdn(request.msisdn());

        var customer = customerRepository
                .findById(request.customerId())
                .orElseThrow(() -> new IllegalArgumentException("Cliente no encontrado"));

        var ticket = ticketRequestRepository
                .findById(request.ticketId())
                .orElseThrow(() -> new IllegalArgumentException("Ticket no encontrado"));

        if (!ticket.getCustomerId().equals(request.customerId())) {
            throw new IllegalArgumentException("El ticket no pertenece al cliente indicado");
        }

        if (ticket.getClosedAt() != null || "CLOSED".equalsIgnoreCase(ticket.getStatus())) {
            throw new IllegalArgumentException("El ticket está cerrado; no aplica compensación");
        }

        if (mobileTopUpEntityRepository.existsByTicketId(request.ticketId())) {
            throw new IllegalArgumentException("Ya existe una recarga registrada para este ticket");
        }

        assertMsisdnMatchesCustomerPhoneIfPresent(customer, msisdnNorm);

        enforceCooldown(request.customerId(), msisdnNorm);

        MobileTopUpEntity entity = mobileTopUpEntityMapper.fromRequest(request);
        entity.setMsisdn(msisdnNorm);
        entity.setBenefitId(String.valueOf(request.ticketId()));
        entity.setDataGb(MobileTopUpEntity.FIXED_DATA_GB);
        entity.setStatus(BenefitStatus.APPLIED);

        LocalDateTime now = LocalDateTime.now();
        entity.setValidUntil(now.plusDays(PACKAGE_VALIDITY_DAYS));
        entity.setNextEligibleAt(now.plusDays(COOLDOWN_DAYS));
        entity.setMessage(
                "Compensación de "
                        + MobileTopUpEntity.FIXED_DATA_GB
                        + " GB aplicada (ticket "
                        + request.ticketId()
                        + ")");

        MobileTopUpEntity saved = mobileTopUpEntityRepository.save(entity);
        log.info(
                "Mobile top-up APPLIED customerId={} ticketId={} msisdn={}",
                saved.getCustomerId(),
                saved.getTicketId(),
                saved.getMsisdn());

        return mobileTopUpEntityMapper.toDto(saved);
    }

    private static void requireNonNullIds(MobileTopUpRequestDTO request) {
        if (request.customerId() == null || request.ticketId() == null) {
            throw new IllegalArgumentException("customerId y ticketId son obligatorios");
        }
    }

    /**
     * Deja solo dígitos para comparar y guardar; exige longitud mínima razonable.
     */
    private static String normalizeMsisdn(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("El número de línea (msisdn) es obligatorio");
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.length() < 8) {
            throw new IllegalArgumentException("Número de línea inválido");
        }
        return digits;
    }

    /**
     * Si el cliente tiene teléfono cargado en CRM, debe coincidir con la línea a recargar (misma normalización).
     */
    private static void assertMsisdnMatchesCustomerPhoneIfPresent(
            CustomerEntity customer, String msisdnNorm) {
        String phone = customer.getPhone();
        if (phone == null || phone.isBlank()) {
            return;
        }
        String phoneNorm = phone.replaceAll("\\D", "");
        if (phoneNorm.isEmpty()) {
            return;
        }
        if (!phoneNorm.equals(msisdnNorm)) {
            throw new IllegalArgumentException(
                    "El número indicado no coincide con el teléfono registrado del cliente");
        }
    }

    /**
     * No permite otra recarga exitosa en la misma línea hasta {@link #COOLDOWN_DAYS} después de la última
     * {@link BenefitStatus#APPLIED} (según {@code nextEligibleAt} guardado en ese registro).
     */
    private void enforceCooldown(Long customerId, String msisdnNorm) {
        mobileTopUpEntityRepository
                .findFirstByCustomerIdAndMsisdnAndStatusOrderByCreatedAtDesc(
                        customerId, msisdnNorm, BenefitStatus.APPLIED)
                .ifPresent(
                        last -> {
                            LocalDateTime next = last.getNextEligibleAt();
                            if (next != null && next.isAfter(LocalDateTime.now())) {
                                throw new IllegalArgumentException(
                                        "Debe esperar hasta "
                                                + next
                                                + " para otra recarga en esta línea");
                            }
                        });
    }
}
