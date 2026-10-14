package com.cliente.cliente_back.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cliente.cliente_back.domain.model.ServiceSubscription;
import com.cliente.cliente_back.domain.service.DomainOwnershipValidator;

class DomainOwnershipValidatorTest {

    @Test
    @DisplayName("assertServiceBelongsToCustomer: permite cuando el customerId coincide")
    void assertServiceBelongsToCustomer_success() {
        ServiceSubscription service = ServiceSubscription.builder()
                .id(10L)
                .customerId(100L)
                .serviceName("Fibra 300MB")
                .build();

        assertDoesNotThrow(() -> DomainOwnershipValidator.assertServiceBelongsToCustomer(service, 100L));
    }

    @Test
    @DisplayName("assertServiceBelongsToCustomer: lanza excepción cuando el customerId no coincide")
    void assertServiceBelongsToCustomer_mismatch() {
        ServiceSubscription service = ServiceSubscription.builder()
                .id(10L)
                .customerId(100L)
                .serviceName("Fibra 300MB")
                .build();

        assertThrows(IllegalArgumentException.class,
                () -> DomainOwnershipValidator.assertServiceBelongsToCustomer(service, 999L));
    }

    @Test
    @DisplayName("assertServiceBelongsToCustomer: lanza excepción cuando el servicio es nulo")
    void assertServiceBelongsToCustomer_nullService() {
        assertThrows(IllegalArgumentException.class,
                () -> DomainOwnershipValidator.assertServiceBelongsToCustomer((ServiceSubscription) null, 100L));
    }
}

