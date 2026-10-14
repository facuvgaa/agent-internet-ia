package com.cliente.cliente_back.infrastructure.web.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.cliente.cliente_back.application.dto.AvailableOfferingsForCustomerDTO;
import com.cliente.cliente_back.domain.port.in.CustomerAvailableOfferingUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/available-services")
@RequiredArgsConstructor
@Slf4j
public class AvailableServicesController {

    private final CustomerAvailableOfferingUseCase customerAvailableOfferingUseCase;

    @GetMapping("/customers/{customerId}/offerings")
    public ResponseEntity<AvailableOfferingsForCustomerDTO> listOfferingsForCustomer(
            @PathVariable Long customerId) {
        try {
            return ResponseEntity.ok(customerAvailableOfferingUseCase.listOfferingsForCustomer(customerId));
        } catch (IllegalArgumentException ex) {
            log.warn("listOfferingsForCustomer: {}", ex.getMessage());
            return ResponseEntity.notFound().build();
        }
    }
}

