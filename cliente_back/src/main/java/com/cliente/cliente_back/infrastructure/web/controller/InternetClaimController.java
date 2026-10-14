package com.cliente.cliente_back.infrastructure.web.controller;

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

import com.cliente.cliente_back.application.dto.BillingDTO;
import com.cliente.cliente_back.application.dto.ConnectionResetRequestDTO;
import com.cliente.cliente_back.application.dto.ConnectionResetResponseDTO;
import com.cliente.cliente_back.application.dto.CustomerDTO;
import com.cliente.cliente_back.application.dto.MobileTopUpRequestDTO;
import com.cliente.cliente_back.application.dto.MobileTopUpResponseDTO;
import com.cliente.cliente_back.application.dto.NetworkDiagnosticRequestDTO;
import com.cliente.cliente_back.application.dto.NetworkDiagnosticResponseDTO;
import com.cliente.cliente_back.application.dto.PaymentPromiseDTO;
import com.cliente.cliente_back.application.dto.ServicesDTO;
import com.cliente.cliente_back.application.dto.TicketRequestDTO;
import com.cliente.cliente_back.domain.port.in.BillingUseCase;
import com.cliente.cliente_back.domain.port.in.ConnectionResetUseCase;
import com.cliente.cliente_back.domain.port.in.CustomerUseCase;
import com.cliente.cliente_back.domain.port.in.MobileTopUpUseCase;
import com.cliente.cliente_back.domain.port.in.NetworkDiagnosticUseCase;
import com.cliente.cliente_back.domain.port.in.PaymentPromiseUseCase;
import com.cliente.cliente_back.domain.port.in.ServicesUseCase;
import com.cliente.cliente_back.domain.port.in.TicketRequestUseCase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/internet-ia")
@RequiredArgsConstructor
@Slf4j
public class InternetClaimController {

    private final CustomerUseCase customerUseCase;
    private final ServicesUseCase servicesUseCase;
    private final TicketRequestUseCase ticketRequestUseCase;
    private final BillingUseCase billingUseCase;
    private final PaymentPromiseUseCase paymentPromiseUseCase;
    private final NetworkDiagnosticUseCase networkDiagnosticUseCase;
    private final ConnectionResetUseCase connectionResetUseCase;
    private final MobileTopUpUseCase mobileTopUpUseCase;

    @GetMapping("/customers/{customerId}")
    public ResponseEntity<CustomerDTO> getCustomer(@PathVariable Long customerId) {
        return customerUseCase.getCustomerById(customerId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/customers/services/{customerId}")
    public ResponseEntity<List<ServicesDTO>> getCustomerServices(@PathVariable Long customerId) {
        var services = servicesUseCase.findAllByCustomerId(customerId);
        return services.isEmpty()
                ? ResponseEntity.notFound().build()
                : ResponseEntity.ok(services);
    }

    @PostMapping("/tickets")
    public ResponseEntity<TicketRequestDTO> createTicket(@RequestBody TicketRequestDTO request) {
        TicketRequestDTO created = ticketRequestUseCase.createTicket(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/billing/customer/{customerId}")
    public ResponseEntity<List<BillingDTO>> getBilling(@PathVariable Long customerId) {
        var billings = billingUseCase.findAllBilling(customerId);
        return billings.isEmpty()
                ? ResponseEntity.notFound().build()
                : ResponseEntity.ok(billings);
    }

    @GetMapping("/billing/customer/{customerId}/lookup")
    public ResponseEntity<BillingDTO> lookupBilling(
            @PathVariable Long customerId, @RequestParam("invoiceNumber") String invoiceNumber) {
        try {
            return billingUseCase
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
        PaymentPromiseDTO created = paymentPromiseUseCase.createPromise(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/mobile-topups")
    public ResponseEntity<MobileTopUpResponseDTO> grantMobileTopUp(@RequestBody MobileTopUpRequestDTO request) {
        try {
            MobileTopUpResponseDTO created = mobileTopUpUseCase.grantTopUp(request);
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
            ConnectionResetResponseDTO created = connectionResetUseCase.requestReset(request);
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
            NetworkDiagnosticResponseDTO created = networkDiagnosticUseCase.runDiagnostic(request);
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
            var list = networkDiagnosticUseCase.findByCustomerIdAndServiceIdOrderByCreatedAtDesc(
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
            return networkDiagnosticUseCase
                    .findFirstByCustomerIdAndServiceIdOrderByCreatedAtDesc(customerId, serviceId)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (IllegalArgumentException ex) {
            log.warn("getLatestNetworkDiagnostic: {}", ex.getMessage());
            return ResponseEntity.notFound().build();
        }
    }
}
