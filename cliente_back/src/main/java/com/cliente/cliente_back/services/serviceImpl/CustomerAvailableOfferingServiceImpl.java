package com.cliente.cliente_back.services.serviceImpl;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.cliente.cliente_back.available.AvailableOfferingSpec;
import com.cliente.cliente_back.dto.AvailableOfferingItemDTO;
import com.cliente.cliente_back.dto.AvailableOfferingsForCustomerDTO;
import com.cliente.cliente_back.entities.ServicesEntity;
import com.cliente.cliente_back.repositories.CustomerRepository;
import com.cliente.cliente_back.repositories.ServiceRepository;
import com.cliente.cliente_back.services.CustomerAvailableOfferingService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CustomerAvailableOfferingServiceImpl implements CustomerAvailableOfferingService {

    private final CustomerRepository customerRepository;
    private final ServiceRepository serviceRepository;

    @Override
    public AvailableOfferingsForCustomerDTO listOfferingsForCustomer(Long customerId) {
        if (customerId == null) {
            throw new IllegalArgumentException("customerId es obligatorio");
        }
        if (!customerRepository.existsById(customerId)) {
            throw new IllegalArgumentException("Cliente no encontrado");
        }

        List<ServicesEntity> customerServices = serviceRepository.findByCustomerId_Id(customerId);

        List<AvailableOfferingItemDTO> items =
                Arrays.stream(AvailableOfferingSpec.values())
                        .map(spec -> toItem(spec, customerServices))
                        .collect(Collectors.toList());

        return new AvailableOfferingsForCustomerDTO(customerId, items);
    }

    private static AvailableOfferingItemDTO toItem(
            AvailableOfferingSpec spec, List<ServicesEntity> customerServices) {
        String matchType = spec.existingServiceTypeMatch();
        int current =
                (int)
                        customerServices.stream()
                                .filter(s -> matchesServiceType(s, matchType))
                                .filter(CustomerAvailableOfferingServiceImpl::isActiveLine)
                                .count();

        int max = spec.maxActiveOfType();
        boolean eligible = current < max;
        String reason =
                eligible
                        ? null
                        : "Ya alcanzó el máximo de "
                                + max
                                + " servicio(s) tipo \""
                                + matchType
                                + "\" en esta cuenta.";

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

    private static boolean matchesServiceType(ServicesEntity s, String expectedType) {
        if (s.getServiceType() == null) {
            return false;
        }
        return s.getServiceType().equalsIgnoreCase(expectedType);
    }

    /**
     * Líneas suspendidas o dadas de baja no consumen cupo para nuevas altas.
     */
    private static boolean isActiveLine(ServicesEntity s) {
        String status = s.getStatus();
        if (status == null || status.isBlank()) {
            return true;
        }
        String lower = status.toLowerCase();
        return !lower.contains("suspend")
                && !lower.contains("baja")
                && !lower.contains("cancel");
    }
}
