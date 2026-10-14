package com.cliente.cliente_back.domain.port.in;

import com.cliente.cliente_back.application.dto.AvailableOfferingsForCustomerDTO;

public interface CustomerAvailableOfferingUseCase {
    AvailableOfferingsForCustomerDTO listOfferingsForCustomer(Long customerId);
}

