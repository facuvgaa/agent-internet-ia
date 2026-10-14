package com.cliente.cliente_back.domain.service;

import com.cliente.cliente_back.domain.model.ServiceSubscription;

public final class DomainOwnershipValidator {

    private DomainOwnershipValidator() {}

    public static void assertServiceBelongsToCustomer(ServiceSubscription service, Long customerId) {
        if (service == null) {
            throw new IllegalArgumentException("Servicio no encontrado");
        }
        if (customerId == null || service.getCustomerId() == null || !service.getCustomerId().equals(customerId)) {
            throw new IllegalArgumentException("El servicio no pertenece al cliente indicado");
        }
    }

    public static void assertServiceBelongsToCustomer(Long serviceOwnerId, Long requestedCustomerId) {
        if (serviceOwnerId == null || requestedCustomerId == null || !serviceOwnerId.equals(requestedCustomerId)) {
            throw new IllegalArgumentException("El servicio no pertenece al cliente");
        }
    }
}
