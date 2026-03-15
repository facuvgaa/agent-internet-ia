package com.cliente.cliente_back.controllers;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cliente.cliente_back.dto.CustomerDTO;
import com.cliente.cliente_back.dto.ServicesDTO;
import com.cliente.cliente_back.dto.TicketRequestDTO;
import com.cliente.cliente_back.services.CustomerService;
import com.cliente.cliente_back.services.ServicesService;
import com.cliente.cliente_back.services.TicketRequestService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/internet-ia")
@RequiredArgsConstructor
@Slf4j
public class InternetClaimController {
    private final CustomerService customerService;
    private final ServicesService servicesService;
    private final TicketRequestService ticketRequestService;
    @GetMapping("/customers/{customerId}")
    public ResponseEntity<CustomerDTO> getCustomer(@PathVariable Long customerId){
        return customerService.getCustomerById(customerId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/customers/services/{customerId}")
    public ResponseEntity<List<ServicesDTO>> getCustomerServices(@PathVariable Long customerId){
        var services = servicesService.findAllByCustomerId(customerId);
        return services.isEmpty()
            ? ResponseEntity.notFound().build()
            : ResponseEntity.ok(services);
    }

    @PostMapping("/tickets")
    public ResponseEntity<TicketRequestDTO> createTicket(@RequestBody TicketRequestDTO request) {
        TicketRequestDTO created = ticketRequestService.createTicket(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
} 
