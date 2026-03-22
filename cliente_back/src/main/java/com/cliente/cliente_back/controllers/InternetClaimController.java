package com.cliente.cliente_back.controllers;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cliente.cliente_back.dto.BillingDTO;
import com.cliente.cliente_back.dto.ConnectionResetRequestDTO;
import com.cliente.cliente_back.dto.ConnectionResetResponseDTO;
import com.cliente.cliente_back.dto.CustomerDTO;
import com.cliente.cliente_back.dto.MobileTopUpRequestDTO;
import com.cliente.cliente_back.dto.MobileTopUpResponseDTO;
import com.cliente.cliente_back.dto.NetworkDiagnosticRequestDTO;
import com.cliente.cliente_back.dto.NetworkDiagnosticResponseDTO;
import com.cliente.cliente_back.dto.PaymentPromiseDTO;
import com.cliente.cliente_back.dto.ServicesDTO;
import com.cliente.cliente_back.dto.TicketRequestDTO;
import com.cliente.cliente_back.services.BillingService;
import com.cliente.cliente_back.services.ConnectionResetService;
import com.cliente.cliente_back.services.CustomerService;
import com.cliente.cliente_back.services.MobileTopUpEntityService;
import com.cliente.cliente_back.services.NetworkDiagnosticService;
import com.cliente.cliente_back.services.PaymentPromiseService;
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
    private final BillingService billingService;
    private final PaymentPromiseService paymentPromiseService;
    private final NetworkDiagnosticService networkDiagnosticService;
    private final ConnectionResetService connectionResetService;
    private final MobileTopUpEntityService mobileTopUpEntityService;

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

    @GetMapping("/billing/customer/{customerId}")
    public ResponseEntity<List<BillingDTO>> getBilling(@PathVariable Long customerId){
        var billings = billingService.findAllBilling(customerId);
        return billings.isEmpty()
            ? ResponseEntity.notFound().build()
            : ResponseEntity.ok(billings);
    }
    @GetMapping("/billing/customer/{customerId}/lookup")
    public ResponseEntity<BillingDTO> lookupBilling(
            @PathVariable Long customerId, @RequestParam("invoiceNumber") String invoiceNumber) {
        try {
            return billingService
                    .findBillingByCustomerAndInvoiceNumber(customerId, invoiceNumber)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (IllegalArgumentException ex) {
            log.warn("lookupBilling: {}", ex.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/payment-promises")
    public ResponseEntity<PaymentPromiseDTO> createPaymentPromise(@RequestBody PaymentPromiseDTO request) {
        PaymentPromiseDTO created = paymentPromiseService.createPromise(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/mobile-topups")
    public ResponseEntity<MobileTopUpResponseDTO> grantMobileTopUp(@RequestBody MobileTopUpRequestDTO request) {
        try {
            MobileTopUpResponseDTO created = mobileTopUpEntityService.grantTopUp(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException ex) {
            log.warn("grantMobileTopUp: {}", ex.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/connection-resets")
    public ResponseEntity<ConnectionResetResponseDTO> requestConnectionReset(
            @RequestBody ConnectionResetRequestDTO request) {
        try {
            ConnectionResetResponseDTO created = connectionResetService.requestReset(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException ex) {
            log.warn("requestConnectionReset: {}", ex.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/network-diagnostics")
    public ResponseEntity<NetworkDiagnosticResponseDTO> runNetworkDiagnostic(
            @RequestBody NetworkDiagnosticRequestDTO request) {
        try {
            NetworkDiagnosticResponseDTO created = networkDiagnosticService.runDiagnostic(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException ex) {
            log.warn("runNetworkDiagnostic: {}", ex.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/network-diagnostics/customers/{customerId}/services/{serviceId}")
    public ResponseEntity<List<NetworkDiagnosticResponseDTO>> listNetworkDiagnostics(
            @PathVariable Long customerId,
            @PathVariable Long serviceId) {
        try {
            var list = networkDiagnosticService.findByCustomerIdAndServiceIdOrderByCreatedAtDesc(
                    customerId, serviceId);
            return list.isEmpty()
                    ? ResponseEntity.notFound().build()
                    : ResponseEntity.ok(list);
        } catch (IllegalArgumentException ex) {
            log.warn("listNetworkDiagnostics: {}", ex.getMessage());
            return ResponseEntity.notFound().build();
        }
    }
    @GetMapping("/network-diagnostics/customers/{customerId}/services/{serviceId}/latest")
    public ResponseEntity<NetworkDiagnosticResponseDTO> getLatestNetworkDiagnostic(
            @PathVariable Long customerId,
            @PathVariable Long serviceId) {
        try {
            return networkDiagnosticService
                    .findFirstByCustomerIdAndServiceIdOrderByCreatedAtDesc(customerId, serviceId)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (IllegalArgumentException ex) {
            log.warn("getLatestNetworkDiagnostic: {}", ex.getMessage());
            return ResponseEntity.notFound().build();
        }
    }
} 
