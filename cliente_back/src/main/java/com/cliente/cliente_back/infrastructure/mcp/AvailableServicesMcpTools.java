package com.cliente.cliente_back.infrastructure.mcp;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import com.cliente.cliente_back.application.dto.AvailableOfferingsForCustomerDTO;
import com.cliente.cliente_back.domain.port.in.CustomerAvailableOfferingUseCase;

@Service
public class AvailableServicesMcpTools {

    private final CustomerAvailableOfferingUseCase customerAvailableOfferingUseCase;

    public AvailableServicesMcpTools(CustomerAvailableOfferingUseCase customerAvailableOfferingUseCase) {
        this.customerAvailableOfferingUseCase = customerAvailableOfferingUseCase;
    }

    @Tool(name = "list_available_offerings",
          description = "Lista los add-ons y servicios adicionales disponibles para contratar según el perfil del cliente (cupos disponibles por tipo de oferta).")
    public AvailableOfferingsForCustomerDTO listAvailableOfferings(
            @ToolParam(description = "ID numérico del cliente") Long customerId) {
        return customerAvailableOfferingUseCase.listOfferingsForCustomer(customerId);
    }
}
