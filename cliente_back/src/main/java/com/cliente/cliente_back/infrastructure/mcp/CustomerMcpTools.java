package com.cliente.cliente_back.infrastructure.mcp;

import java.util.List;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import com.cliente.cliente_back.application.dto.CustomerDTO;
import com.cliente.cliente_back.application.dto.MobileTopUpRequestDTO;
import com.cliente.cliente_back.application.dto.MobileTopUpResponseDTO;
import com.cliente.cliente_back.application.dto.ServicesDTO;
import com.cliente.cliente_back.application.dto.TicketRequestDTO;
import com.cliente.cliente_back.domain.port.in.CustomerUseCase;
import com.cliente.cliente_back.domain.port.in.MobileTopUpUseCase;
import com.cliente.cliente_back.domain.port.in.ServicesUseCase;
import com.cliente.cliente_back.domain.port.in.TicketRequestUseCase;

@Service
public class CustomerMcpTools {

    private final CustomerUseCase customerUseCase;
    private final ServicesUseCase servicesUseCase;
    private final TicketRequestUseCase ticketRequestUseCase;
    private final MobileTopUpUseCase mobileTopUpUseCase;

    public CustomerMcpTools(
            CustomerUseCase customerUseCase,
            ServicesUseCase servicesUseCase,
            TicketRequestUseCase ticketRequestUseCase,
            MobileTopUpUseCase mobileTopUpUseCase) {
        this.customerUseCase = customerUseCase;
        this.servicesUseCase = servicesUseCase;
        this.ticketRequestUseCase = ticketRequestUseCase;
        this.mobileTopUpUseCase = mobileTopUpUseCase;
    }

    @Tool(name = "get_customer_info",
          description = "Retorna datos del cliente (nombre, email, teléfono, estado de cuenta). Usar antes de cualquier gestión para confirmar identidad y estado.")
    public CustomerDTO getCustomerInfo(
            @ToolParam(description = "ID numérico del cliente") Long customerId) {
        return customerUseCase.getCustomerById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Cliente no encontrado: " + customerId));
    }

    @Tool(name = "get_customer_service",
          description = "Lista los servicios/líneas contratadas del cliente (nombre del plan, dirección, precio mensual, descuento vigente, id de servicio). Necesario para operaciones sobre líneas específicas.")
    public List<ServicesDTO> getCustomerService(
            @ToolParam(description = "ID numérico del cliente") Long customerId) {
        return servicesUseCase.findAllByCustomerId(customerId);
    }

    @Tool(name = "create_ticket",
          description = "Abre un ticket de reclamo técnico o de facturación. Devuelve el id y estado del ticket creado.")
    public TicketRequestDTO createTicket(
            @ToolParam(description = "ID numérico del cliente") Long customerId,
            @ToolParam(description = "Descripción del reclamo (máx 200 chars)") String subject,
            @ToolParam(description = "Prioridad del ticket: alta, media, baja") String priority) {
        return ticketRequestUseCase.createTicket(new TicketRequestDTO(null, customerId, subject, priority));
    }

    @Tool(name = "grant_mobile_topup",
          description = "Otorga compensación de 10 GB móviles al cliente como resarcimiento por una falla técnica registrada en un ticket. Requiere el número de teléfono normalizado.")
    public MobileTopUpResponseDTO grantMobileTopup(
            @ToolParam(description = "ID numérico del cliente") Long customerId,
            @ToolParam(description = "ID del ticket asociado a la falla") Long ticketId,
            @ToolParam(description = "Número de teléfono normalizado (MSISDN)") String msisdn,
            @ToolParam(description = "Motivo de la compensación (opcional)", required = false) String reason) {
        return mobileTopUpUseCase.grantTopUp(new MobileTopUpRequestDTO(customerId, ticketId, msisdn, reason));
    }
}
