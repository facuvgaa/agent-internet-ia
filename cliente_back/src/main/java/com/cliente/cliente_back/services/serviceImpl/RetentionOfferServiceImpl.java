package com.cliente.cliente_back.services.serviceImpl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.cliente.cliente_back.dto.RetentionEligibilityResponseDTO;
import com.cliente.cliente_back.dto.RetentionPreviewRequestDTO;
import com.cliente.cliente_back.dto.RetentionPreviewResponseDTO;
import com.cliente.cliente_back.dto.RetentionTierDTO;
import com.cliente.cliente_back.entities.ServicesEntity;
import com.cliente.cliente_back.repositories.CustomerRepository;
import com.cliente.cliente_back.repositories.ServiceRepository;
import com.cliente.cliente_back.retention.RetentionOfferConstants;
import com.cliente.cliente_back.retention.RetentionTierSpec;
import com.cliente.cliente_back.services.RetentionOfferService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RetentionOfferServiceImpl implements RetentionOfferService {

    private static final String CURRENCY_DEFAULT = "ARS";

    private final CustomerRepository customerRepository;
    private final ServiceRepository serviceRepository;

    @Override
    public List<RetentionTierDTO> listTiers() {
        return Arrays.stream(RetentionTierSpec.values())
                .map(t -> new RetentionTierDTO(t.level(), t.discountPercent(), t.durationMonths()))
                .collect(Collectors.toList());
    }

    @Override
    public RetentionEligibilityResponseDTO getEligibility(Long customerId, Long serviceId) {
        if (customerId == null) {
            return new RetentionEligibilityResponseDTO(
                    null,
                    serviceId,
                    false,
                    List.of(),
                    RetentionOfferConstants.APP_PAYMENT_CASHBACK_PERCENT,
                    "customerId es obligatorio");
        }

        if (!customerRepository.existsById(customerId)) {
            return new RetentionEligibilityResponseDTO(
                    customerId,
                    serviceId,
                    false,
                    List.of(),
                    RetentionOfferConstants.APP_PAYMENT_CASHBACK_PERCENT,
                    "Cliente no encontrado");
        }

        if (serviceId != null) {
            ServicesEntity service =
                    serviceRepository.findById(serviceId).orElse(null);
            if (service == null) {
                return new RetentionEligibilityResponseDTO(
                        customerId,
                        serviceId,
                        false,
                        List.of(),
                        RetentionOfferConstants.APP_PAYMENT_CASHBACK_PERCENT,
                        "Servicio no encontrado");
            }
            if (service.getCustomerId() == null
                    || service.getCustomerId().getId() != customerId) {
                return new RetentionEligibilityResponseDTO(
                        customerId,
                        serviceId,
                        false,
                        List.of(),
                        RetentionOfferConstants.APP_PAYMENT_CASHBACK_PERCENT,
                        "El servicio no pertenece al cliente");
            }
        }

        List<Integer> allLevels =
                Arrays.stream(RetentionTierSpec.values()).map(RetentionTierSpec::level).toList();

        return new RetentionEligibilityResponseDTO(
                customerId,
                serviceId,
                true,
                allLevels,
                RetentionOfferConstants.APP_PAYMENT_CASHBACK_PERCENT,
                "Puede cotizar niveles 1 a 4. Al aceptar, se registrará la promoción en otro flujo.");
    }

    @Override
    public RetentionPreviewResponseDTO preview(RetentionPreviewRequestDTO request) {
        if (request.customerId() == null || request.serviceId() == null) {
            throw new IllegalArgumentException("customerId y serviceId son obligatorios");
        }

        RetentionTierSpec tier =
                RetentionTierSpec.fromLevel(request.level())
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Nivel inválido: use un valor entre 1 y 4"));

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

        BigDecimal base = service.getBasePrice();
        BigDecimal after = null;
        if (base != null && base.signum() >= 0) {
            BigDecimal factor =
                    BigDecimal.ONE.subtract(
                            BigDecimal.valueOf(tier.discountPercent())
                                    .divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
            after = base.multiply(factor).setScale(2, RoundingMode.HALF_UP);
        }

        int cashback = RetentionOfferConstants.APP_PAYMENT_CASHBACK_PERCENT;
        String nextBill =
                buildNextBillSummary(base, after, tier.discountPercent(), tier.durationMonths());
        String appExpl =
                "Si abona con nuestra app, tiene un "
                        + cashback
                        + "% de cashback: la factura queda como el monto a pagar menos ese beneficio por uso de la app.";

        return new RetentionPreviewResponseDTO(
                request.customerId(),
                request.serviceId(),
                tier.level(),
                tier.discountPercent(),
                tier.durationMonths(),
                CURRENCY_DEFAULT,
                base,
                after,
                cashback,
                nextBill,
                appExpl);
    }

    private static String buildNextBillSummary(
            BigDecimal base, BigDecimal after, int discountPercent, int durationMonths) {
        if (base != null && after != null) {
            return String.format(
                    "Con el %d%% de descuento por %d meses, el mes próximo la cuota estimada sería de %s "
                            + "(antes del cashback por app).",
                    discountPercent, durationMonths, after);
        }
        return String.format(
                "Con el %d%% de descuento por %d meses, el mes próximo pagará aproximadamente el "
                        + "precio de lista menos ese porcentaje (definir monto cuando el servicio tenga basePrice).",
                discountPercent, durationMonths);
    }
}
