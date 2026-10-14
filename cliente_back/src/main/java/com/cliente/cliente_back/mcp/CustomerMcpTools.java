package com.cliente.cliente_back.mcp;

import com.cliente.cliente_back.dto.CustomerDTO;
import com.cliente.cliente_back.dto.MobileTopUpRequestDTO;
import com.cliente.cliente_back.dto.MobileTopUpResponseDTO;
import com.cliente.cliente_back.dto.ServicesDTO;
import com.cliente.cliente_back.dto.TicketRequestDTO;
import com.cliente.cliente_back.services.CustomerService;
import com.cliente.cliente_back.services.MobileTopUpEntityService;
import com.cliente.cliente_back.services.ServicesService;
import com.cliente.cliente_back.services.TicketRequestService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CustomerMcpTools {

    private final CustomerService customerService;
    private final ServicesService servicesService;
    private final TicketRequestService ticketRequestService;
    private final MobileTopUpEntityService mobileTopUpEntityService;

    public CustomerMcpTools(
            CustomerService customerService,
            ServicesService servicesService,
            TicketRequestService ticketRequestService,
            MobileTopUpEntityService mobileTopUpEntityService) {
        this.customerService = customerService;
        this.servicesService = servicesService;
        this.ticketRequestService = ticketRequestService;
        this.mobileTopUpEntityService = mobileTopUpEntityService;
    }

    @Tool(name = "get_customer_info",
          description = "Retorna datos del cliente (nombre, email, teléfono, estado de cuenta). Usar antes de cualquier gestión para confirmar identidad y estado.")
    public CustomerDTO getCustomerInfo(
            @ToolParam(description = "ID numérico del cliente") Long customerId) {
        return customerService.getCustomerById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Cliente no encontrado: " + customerId));
    }

    @Tool(name = "get_customer_service",
          description = "Lista los servicios/líneas contratadas del cliente (nombre del plan, dirección, precio mensual, descuento vigente, id de servicio). Necesario para operaciones sobre líneas específicas.")
    public List<ServicesDTO> getCustomerService(
            @ToolParam(description = "ID numérico del cliente") Long customerId) {
        return servicesService.findAllByCustomerId(customerId);
    }

    @Tool(name = "create_ticket",
          description = "Abre un ticket de reclamo técnico o de facturación. Devuelve el id y estado del ticket creado.")
    public TicketRequestDTO createTicket(
            @ToolParam(description = "ID numérico del cliente") Long customerId,
            @ToolParam(description = "Descripción del reclamo (máx 200 chars)") String subject,
            @ToolParam(description = "Prioridad del ticket: alta, media, baja") String priority) {
        return ticketRequestService.createTicket(new TicketRequestDTO(null, customerId, subject, priority));
    }

    @Tool(name = "grant_mobile_topup",
          description = "Otorga compensación de 10 GB móviles al cliente como resarcimiento por una falla técnica registrada en un ticket. Requiere el número de teléfono normalizado.")
    public MobileTopUpResponseDTO grantMobileTopup(
            @ToolParam(description = "ID numérico del cliente") Long customerId,
            @ToolParam(description = "ID del ticket asociado a la falla") Long ticketId,
            @ToolParam(description = "Número de teléfono normalizado (MSISDN)") String msisdn,
            @ToolParam(description = "Motivo de la compensación (opcional)", required = false) String reason) {
        return mobileTopUpEntityService.grantTopUp(new MobileTopUpRequestDTO(customerId, ticketId, msisdn, reason));
    }
}
