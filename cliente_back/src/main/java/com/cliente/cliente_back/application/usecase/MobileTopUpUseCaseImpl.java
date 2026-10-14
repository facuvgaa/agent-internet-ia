package com.cliente.cliente_back.application.usecase;

import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.cliente.cliente_back.application.dto.MobileTopUpRequestDTO;
import com.cliente.cliente_back.application.dto.MobileTopUpResponseDTO;
import com.cliente.cliente_back.domain.model.BenefitStatus;
import com.cliente.cliente_back.domain.model.Customer;
import com.cliente.cliente_back.domain.model.MobileTopUp;
import com.cliente.cliente_back.domain.model.TicketRequest;
import com.cliente.cliente_back.domain.port.in.MobileTopUpUseCase;
import com.cliente.cliente_back.domain.port.out.CustomerRepositoryPort;
import com.cliente.cliente_back.domain.port.out.MobileTopUpRepositoryPort;
import com.cliente.cliente_back.domain.port.out.TicketRequestRepositoryPort;
import com.cliente.cliente_back.domain.service.MobileTopUpCooldownValidator;
import com.cliente.cliente_back.infrastructure.persistence.mapper.MobileTopUpEntityMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class MobileTopUpUseCaseImpl implements MobileTopUpUseCase {

    private final MobileTopUpRepositoryPort mobileTopUpRepositoryPort;
    private final MobileTopUpEntityMapper mobileTopUpEntityMapper;
    private final TicketRequestRepositoryPort ticketRequestRepositoryPort;
    private final CustomerRepositoryPort customerRepositoryPort;

    @Override
    @Transactional
    public MobileTopUpResponseDTO grantTopUp(MobileTopUpRequestDTO request) {
        if (request == null || request.customerId() == null || request.ticketId() == null) {
            throw new IllegalArgumentException("customerId y ticketId son obligatorios");
        }
        String msisdnNorm = normalizeMsisdn(request.msisdn());

        Customer customer = customerRepositoryPort.findById(request.customerId())
                .orElseThrow(() -> new IllegalArgumentException("Cliente no encontrado"));

        TicketRequest ticket = ticketRequestRepositoryPort.findById(request.ticketId())
                .orElseThrow(() -> new IllegalArgumentException("Ticket no encontrado"));

        if (!ticket.getCustomerId().equals(request.customerId())) {
            throw new IllegalArgumentException("El ticket no pertenece al cliente indicado");
        }

        if (ticket.isClosed()) {
            throw new IllegalArgumentException("El ticket está cerrado; no aplica compensación");
        }

        if (mobileTopUpRepositoryPort.existsByTicketId(request.ticketId())) {
            throw new IllegalArgumentException("Ya existe una recarga registrada para este ticket");
        }

        assertMsisdnMatchesCustomerPhoneIfPresent(customer, msisdnNorm);

        var lastTopUp = mobileTopUpRepositoryPort.findFirstByCustomerIdAndMsisdnAndStatusOrderByCreatedAtDesc(
                request.customerId(), msisdnNorm, BenefitStatus.APPLIED);
        LocalDateTime now = LocalDateTime.now();
        lastTopUp.ifPresent(t -> MobileTopUpCooldownValidator.validateCooldown(t.getNextEligibleAt(), now));

        MobileTopUp topUp = MobileTopUp.builder()
                .benefitId(String.valueOf(request.ticketId()))
                .ticketId(request.ticketId())
                .customerId(request.customerId())
                .msisdn(msisdnNorm)
                .dataGb(MobileTopUp.FIXED_DATA_GB)
                .reason(request.reason())
                .status(BenefitStatus.APPLIED)
                .validUntil(now.plusDays(MobileTopUp.PACKAGE_VALIDITY_DAYS))
                .nextEligibleAt(now.plusDays(MobileTopUp.COOLDOWN_DAYS))
                .message("Compensación de " + MobileTopUp.FIXED_DATA_GB + " GB aplicada (ticket " + request.ticketId() + ")")
                .createdAt(now)
                .build();

        MobileTopUp saved = mobileTopUpRepositoryPort.save(topUp);
        log.info("Mobile top-up APPLIED customerId={} ticketId={} msisdn={}", saved.getCustomerId(), saved.getTicketId(), saved.getMsisdn());
        return mobileTopUpEntityMapper.toDto(saved);
    }

    private static String normalizeMsisdn(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replaceAll("[^0-9]", "");
    }

    private static void assertMsisdnMatchesCustomerPhoneIfPresent(Customer customer, String msisdnNorm) {
        if (customer.getPhone() == null || customer.getPhone().isBlank()) {
            return;
        }
        String custPhoneNorm = normalizeMsisdn(customer.getPhone());
        if (!custPhoneNorm.equals(msisdnNorm)) {
            throw new IllegalArgumentException("El teléfono no coincide con el registrado para el cliente");
        }
    }
}
