package com.cliente.cliente_back.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cliente.cliente_back.dto.AvailableOfferingsForCustomerDTO;
import com.cliente.cliente_back.services.CustomerAvailableOfferingService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/available-services")
@RequiredArgsConstructor
@Slf4j
public class AvailableServicesController {

    private final CustomerAvailableOfferingService customerAvailableOfferingService;

    @GetMapping("/customers/{customerId}/offerings")
    public ResponseEntity<AvailableOfferingsForCustomerDTO> listOfferingsForCustomer(
            @PathVariable Long customerId) {
        try {
            return ResponseEntity.ok(customerAvailableOfferingService.listOfferingsForCustomer(customerId));
        } catch (IllegalArgumentException ex) {
            log.warn("listOfferingsForCustomer: {}", ex.getMessage());
            return ResponseEntity.notFound().build();
        }
    }
}
