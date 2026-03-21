package com.cliente.cliente_back.services;

import com.cliente.cliente_back.dto.AvailableOfferingsForCustomerDTO;

public interface CustomerAvailableOfferingService {

    AvailableOfferingsForCustomerDTO listOfferingsForCustomer(Long customerId);
}
