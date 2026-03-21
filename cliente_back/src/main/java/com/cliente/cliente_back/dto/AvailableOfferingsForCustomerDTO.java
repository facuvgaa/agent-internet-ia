package com.cliente.cliente_back.dto;

import java.util.List;

public record AvailableOfferingsForCustomerDTO(Long customerId, List<AvailableOfferingItemDTO> offerings) {}
