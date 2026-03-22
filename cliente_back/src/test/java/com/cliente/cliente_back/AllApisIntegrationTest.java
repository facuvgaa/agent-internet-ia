package com.cliente.cliente_back;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.cliente.cliente_back.dto.BillingStatus;
import com.cliente.cliente_back.dto.TicketRequestDTO;
import com.cliente.cliente_back.entities.BillingEntity;
import com.cliente.cliente_back.entities.CustomerEntity;
import com.cliente.cliente_back.entities.ServicesEntity;
import com.cliente.cliente_back.entities.TicketRequestEntity;
import com.cliente.cliente_back.repositories.BillingRepository;
import com.cliente.cliente_back.repositories.CustomerRepository;
import com.cliente.cliente_back.repositories.ServiceRepository;
import com.cliente.cliente_back.repositories.TicketRequestRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AllApisIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private BillingRepository billingRepository;

    @Autowired
    private TicketRequestRepository ticketRequestRepository;

    private long customerId;
    private long serviceId;
    private long billingId;
    private long ticketId;

    @BeforeEach
    void seedData() {
        CustomerEntity c = new CustomerEntity();
        c.setName("Integration Test");
        c.setEmail("u-" + UUID.randomUUID() + "@test.local");
        c.setPhone("5491111111111");
        c.setStatus("Activo");
        c = customerRepository.save(c);
        customerId = c.getId();

        ServicesEntity s = new ServicesEntity();
        s.setCustomerId(c);
        s.setServiceName("Internet Test");
        s.setServiceType("Internet");
        s.setAddress("Test 123");
        s.setBasePrice(new BigDecimal("10000.00"));
        s.setStatus("Activo");
        s = serviceRepository.save(s);
        serviceId = s.getId();

        BillingEntity b = new BillingEntity();
        b.setCustomerId(customerId);
        b.setInvoiceNumber("FAC-TEST-001");
        b.setTotalAmount(new BigDecimal("5000.00"));
        b.setDueDate(LocalDate.now().plusDays(15));
        b.setIssueDate(LocalDate.now());
        b.setPeriodLabel("2026-03");
        b.setStatus(BillingStatus.DUE);
        b = billingRepository.save(b);
        billingId = b.getId();

        TicketRequestEntity t = new TicketRequestEntity();
        t.setCustomerId(customerId);
        t.setSubject("Falla técnica test");
        t.setPriority("HIGH");
        t.setStatus("OPEN");
        t = ticketRequestRepository.save(t);
        ticketId = t.getId();
    }

    @Nested
    @DisplayName("/api/v1/retention")
    class RetentionApi {

        @Test
        void tiers_returnsFourLevels() throws Exception {
            mockMvc.perform(get("/api/v1/retention/tiers"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(4)))
                    .andExpect(jsonPath("$[0].level").value(1))
                    .andExpect(jsonPath("$[3].level").value(4));
        }

        @Test
        void eligibility_unknownCustomer_notEligible() throws Exception {
            mockMvc.perform(get("/api/v1/retention/customers/999999/eligibility"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.eligible").value(false));
        }

        @Test
        void eligibility_withService_ok() throws Exception {
            mockMvc.perform(
                            get("/api/v1/retention/customers/" + customerId + "/eligibility")
                                    .param("serviceId", String.valueOf(serviceId)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.eligible").value(true))
                    .andExpect(jsonPath("$.allowedLevels", hasSize(4)));
        }

        @Test
        void preview_valid_returnsEstimates() throws Exception {
            String body =
                    """
                    {"customerId":%d,"serviceId":%d,"level":2}
                    """
                            .formatted(customerId, serviceId);
            mockMvc.perform(
                            post("/api/v1/retention/preview")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.level").value(2))
                    .andExpect(jsonPath("$.discountPercent").value(50));
        }

        @Test
        void preview_invalidLevel_badRequest() throws Exception {
            String body =
                    """
                    {"customerId":%d,"serviceId":%d,"level":99}
                    """
                            .formatted(customerId, serviceId);
            mockMvc.perform(
                            post("/api/v1/retention/preview")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void applications_createsRecord() throws Exception {
            String body =
                    """
                    {"customerId":%d,"serviceId":%d,"level":1,"idempotencyKey":"idem-ret-1","channel":"TEST"}
                    """
                            .formatted(customerId, serviceId);
            mockMvc.perform(
                            post("/api/v1/retention/applications")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.level").value(1))
                    .andExpect(jsonPath("$.discountPercent").value(25))
                    .andExpect(jsonPath("$.status").value("APPLIED"));
        }
    }

    @Nested
    @DisplayName("/api/v1/available-services")
    class AvailableServicesApi {

        @Test
        void offerings_unknownCustomer_notFound() throws Exception {
            mockMvc.perform(get("/api/v1/available-services/customers/999999/offerings"))
                    .andExpect(status().isNotFound());
        }

        @Test
        void offerings_ok() throws Exception {
            mockMvc.perform(get("/api/v1/available-services/customers/" + customerId + "/offerings"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.customerId").value(customerId))
                    .andExpect(jsonPath("$.offerings", hasSize(greaterThan(0))));
        }
    }

    @Nested
    @DisplayName("/api/v1/internet-ia")
    class InternetIaApi {

        @Test
        void getCustomer_ok() throws Exception {
            mockMvc.perform(get("/api/v1/internet-ia/customers/" + customerId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").exists());
        }

        @Test
        void getCustomer_notFound() throws Exception {
            mockMvc.perform(get("/api/v1/internet-ia/customers/999999"))
                    .andExpect(status().isNotFound());
        }

        @Test
        void getServices_ok() throws Exception {
            mockMvc.perform(get("/api/v1/internet-ia/customers/services/" + customerId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)));
        }

        @Test
        void createTicket_created() throws Exception {
            String body =
                    objectMapper.writeValueAsString(
                            new TicketRequestDTO(null, customerId, "Otro reclamo", "NORMAL"));
            mockMvc.perform(
                            post("/api/v1/internet-ia/tickets")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").exists());
        }

        @Test
        void getBilling_ok() throws Exception {
            mockMvc.perform(get("/api/v1/internet-ia/billing/customer/" + customerId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)));
        }

        @Test
        void lookupBilling_byInternalId_ok() throws Exception {
            mockMvc.perform(
                            get("/api/v1/internet-ia/billing/customer/" + customerId + "/lookup")
                                    .param("invoiceNumber", String.valueOf(billingId)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(billingId))
                    .andExpect(jsonPath("$.invoiceNumber").value("FAC-TEST-001"));
        }

        @Test
        void lookupBilling_byPrintedNumber_ok() throws Exception {
            mockMvc.perform(
                            get("/api/v1/internet-ia/billing/customer/" + customerId + "/lookup")
                                    .param("invoiceNumber", "fac-test-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(billingId));
        }

        @Test
        void lookupBilling_byPeriodLabel_ok() throws Exception {
            mockMvc.perform(
                            get("/api/v1/internet-ia/billing/customer/" + customerId + "/lookup")
                                    .param("invoiceNumber", "2026-03"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.periodLabel").value("2026-03"));
        }

        @Test
        void lookupBilling_notFound() throws Exception {
            mockMvc.perform(
                            get("/api/v1/internet-ia/billing/customer/" + customerId + "/lookup")
                                    .param("invoiceNumber", "NO-EXISTE"))
                    .andExpect(status().isNotFound());
        }

        @Test
        void paymentPromise_created() throws Exception {
            String body =
                    """
                    {"id":null,"customerId":%d,"billingId":%d,"promiseUntil":"%s","status":null}
                    """
                            .formatted(
                                    customerId,
                                    billingId,
                                    LocalDateTime.now().plusDays(7).toString());
            mockMvc.perform(
                            post("/api/v1/internet-ia/payment-promises")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body))
                    .andExpect(status().isCreated());
        }

        @Test
        void mobileTopUp_created() throws Exception {
            String body =
                    """
                    {"customerId":%d,"ticketId":%d,"msisdn":"5491111111111","reason":"test"}
                    """
                            .formatted(customerId, ticketId);
            mockMvc.perform(
                            post("/api/v1/internet-ia/mobile-topups")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.dataGb").value(10));
        }

        @Test
        void connectionReset_created() throws Exception {
            String body =
                    """
                    {"customerId":%d,"serviceId":%d,"reason":"test"}
                    """
                            .formatted(customerId, serviceId);
            mockMvc.perform(
                            post("/api/v1/internet-ia/connection-resets")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.resetJobId").exists());
        }

        @Test
        void networkDiagnostic_createdAndListed() throws Exception {
            String body =
                    """
                    {"customerId":%d,"serviceId":%d,"channel":"TEST"}
                    """
                            .formatted(customerId, serviceId);
            mockMvc.perform(
                            post("/api/v1/internet-ia/network-diagnostics")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body))
                    .andExpect(status().isCreated());

            mockMvc.perform(
                            get("/api/v1/internet-ia/network-diagnostics/customers/"
                                    + customerId
                                    + "/services/"
                                    + serviceId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)));

            mockMvc.perform(
                            get("/api/v1/internet-ia/network-diagnostics/customers/"
                                    + customerId
                                    + "/services/"
                                    + serviceId
                                    + "/latest"))
                    .andExpect(status().isOk());
        }
    }
}
