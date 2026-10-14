package com.cliente.cliente_back.application.dto;

import com.cliente.cliente_back.domain.model.*;

import java.util.List;

public record AvailableOfferingsForCustomerDTO(Long customerId, List<AvailableOfferingItemDTO> offerings) {}
