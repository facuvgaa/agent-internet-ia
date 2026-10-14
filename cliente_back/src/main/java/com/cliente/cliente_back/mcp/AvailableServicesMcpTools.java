package com.cliente.cliente_back.mcp;

import com.cliente.cliente_back.dto.AvailableOfferingsForCustomerDTO;
import com.cliente.cliente_back.services.CustomerAvailableOfferingService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

@Service
public class AvailableServicesMcpTools {

    private final CustomerAvailableOfferingService customerAvailableOfferingService;

    public AvailableServicesMcpTools(CustomerAvailableOfferingService customerAvailableOfferingService) {
        this.customerAvailableOfferingService = customerAvailableOfferingService;
    }

    @Tool(name = "list_available_offerings",
          description = "Lista los add-ons y servicios adicionales disponibles para contratar según el perfil del cliente (cupos disponibles por tipo de oferta).")
    public AvailableOfferingsForCustomerDTO listAvailableOfferings(
            @ToolParam(description = "ID numérico del cliente") Long customerId) {
        return customerAvailableOfferingService.listOfferingsForCustomer(customerId);
    }
}
