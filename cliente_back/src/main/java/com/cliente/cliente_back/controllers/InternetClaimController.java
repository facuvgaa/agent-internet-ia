package com.cliente.cliente_back.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cliente.cliente_back.dto.CustomerDTO;
import com.cliente.cliente_back.services.CustomerService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/internet-ia")
@RequiredArgsConstructor
@Slf4j
public class InternetClaimController {
    private final CustomerService customerService;

    @GetMapping("/customers/{customerId}")
    public ResponseEntity<CustomerDTO> getEntity(@PathVariable Long customerId){
        return customerService.getCustomerById(customerId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
        
} 
