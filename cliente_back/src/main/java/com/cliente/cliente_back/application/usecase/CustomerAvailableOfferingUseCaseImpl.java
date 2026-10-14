package com.cliente.cliente_back.application.usecase;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import com.cliente.cliente_back.application.dto.AvailableOfferingItemDTO;
import com.cliente.cliente_back.application.dto.AvailableOfferingsForCustomerDTO;
import com.cliente.cliente_back.domain.model.AvailableOfferingSpec;
import com.cliente.cliente_back.domain.model.ServiceSubscription;
import com.cliente.cliente_back.domain.port.in.CustomerAvailableOfferingUseCase;
import com.cliente.cliente_back.domain.port.out.CustomerRepositoryPort;
import com.cliente.cliente_back.domain.port.out.ServiceRepositoryPort;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CustomerAvailableOfferingUseCaseImpl implements CustomerAvailableOfferingUseCase {

    private final CustomerRepositoryPort customerRepositoryPort;
    private final ServiceRepositoryPort serviceRepositoryPort;

    @Override
    public AvailableOfferingsForCustomerDTO listOfferingsForCustomer(Long customerId) {
        if (customerId == null) {
            throw new IllegalArgumentException("customerId es obligatorio");
        }
        if (!customerRepositoryPort.existsById(customerId)) {
            throw new IllegalArgumentException("Cliente no encontrado");
        }

        List<ServiceSubscription> customerServices = serviceRepositoryPort.findByCustomerId(customerId);

        List<AvailableOfferingItemDTO> items = Arrays.stream(AvailableOfferingSpec.values())
                .map(spec -> toItem(spec, customerServices))
                .collect(Collectors.toList());

        return new AvailableOfferingsForCustomerDTO(customerId, items);
    }

    private static AvailableOfferingItemDTO toItem(AvailableOfferingSpec spec, List<ServiceSubscription> customerServices) {
        String matchType = spec.existingServiceTypeMatch();
        int current = (int) customerServices.stream()
                .filter(s -> matchesServiceType(s, matchType))
                .filter(ServiceSubscription::isActiveLine)
                .count();

        int max = spec.maxActiveOfType();
        boolean eligible = current < max;
        String reason = eligible
                ? null
                : "Ya alcanzó el máximo de " + max + " servicio(s) tipo \"" + matchType + "\" en esta cuenta.";

        return new AvailableOfferingItemDTO(
                spec.code(),
                spec.displayName(),
                spec.description(),
                matchType,
                max,
                current,
                eligible,
                reason);
    }

    private static boolean matchesServiceType(ServiceSubscription s, String expectedType) {
        if (s.getServiceType() == null) {
            return false;
        }
        return s.getServiceType().equalsIgnoreCase(expectedType);
    }
}

