package com.cliente.cliente_back.application.usecase;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import com.cliente.cliente_back.application.dto.retention.RetentionEligibilityResponseDTO;
import com.cliente.cliente_back.application.dto.retention.RetentionPreviewRequestDTO;
import com.cliente.cliente_back.application.dto.retention.RetentionPreviewResponseDTO;
import com.cliente.cliente_back.application.dto.retention.RetentionTierDTO;
import com.cliente.cliente_back.domain.model.RetentionOfferConstants;
import com.cliente.cliente_back.domain.model.RetentionTierSpec;
import com.cliente.cliente_back.domain.model.ServiceSubscription;
import com.cliente.cliente_back.domain.port.in.RetentionOfferUseCase;
import com.cliente.cliente_back.domain.port.out.CustomerRepositoryPort;
import com.cliente.cliente_back.domain.port.out.ServiceRepositoryPort;
import com.cliente.cliente_back.domain.service.DomainOwnershipValidator;
import com.cliente.cliente_back.domain.service.RetentionPricingService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RetentionOfferUseCaseImpl implements RetentionOfferUseCase {

    private static final String CURRENCY_DEFAULT = "ARS";

    private final CustomerRepositoryPort customerRepositoryPort;
    private final ServiceRepositoryPort serviceRepositoryPort;

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

        if (!customerRepositoryPort.existsById(customerId)) {
            return new RetentionEligibilityResponseDTO(
                    customerId,
                    serviceId,
                    false,
                    List.of(),
                    RetentionOfferConstants.APP_PAYMENT_CASHBACK_PERCENT,
                    "Cliente no encontrado");
        }

        if (serviceId != null) {
            ServiceSubscription service = serviceRepositoryPort.findById(serviceId).orElse(null);
            if (service == null) {
                return new RetentionEligibilityResponseDTO(
                        customerId,
                        serviceId,
                        false,
                        List.of(),
                        RetentionOfferConstants.APP_PAYMENT_CASHBACK_PERCENT,
                        "Servicio no encontrado");
            }
            if (!service.belongsToCustomer(customerId)) {
                return new RetentionEligibilityResponseDTO(
                        customerId,
                        serviceId,
                        false,
                        List.of(),
                        RetentionOfferConstants.APP_PAYMENT_CASHBACK_PERCENT,
                        "El servicio no pertenece al cliente");
            }
        }

        List<Integer> allLevels = Arrays.stream(RetentionTierSpec.values()).map(RetentionTierSpec::level).toList();

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

        RetentionTierSpec tier = RetentionTierSpec.fromLevel(request.level())
                .orElseThrow(() -> new IllegalArgumentException("Nivel inválido: use un valor entre 1 y 4"));

        if (!customerRepositoryPort.existsById(request.customerId())) {
            throw new IllegalArgumentException("Cliente no encontrado");
        }

        ServiceSubscription service = serviceRepositoryPort.findById(request.serviceId())
                .orElseThrow(() -> new IllegalArgumentException("Servicio no encontrado"));

        DomainOwnershipValidator.assertServiceBelongsToCustomer(service, request.customerId());

        BigDecimal base = service.getBasePrice();
        BigDecimal after = RetentionPricingService.calculateDiscountedPrice(base, tier);

        int cashback = RetentionOfferConstants.APP_PAYMENT_CASHBACK_PERCENT;
        String nextBill = RetentionPricingService.buildNextBillSummary(base, after, tier.discountPercent(), tier.durationMonths());
        String appExpl = "Si abona con nuestra app, tiene un "
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
}
