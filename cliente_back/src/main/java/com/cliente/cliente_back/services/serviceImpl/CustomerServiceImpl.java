package com.cliente.cliente_back.services.serviceImpl;

import java.util.Optional;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import com.cliente.cliente_back.services.CustomerService;
import com.cliente.cliente_back.dto.CustomerDTO;
import com.cliente.cliente_back.mappers.CustomerMapper;
import com.cliente.cliente_back.repositories.CustomerRepository;

@Service
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService{
    private final CustomerRepository customerRepository; 
    private final CustomerMapper customerMapper;
    @Override
    public Optional<CustomerDTO> getCustomerById(Long id) {
    return customerRepository.findById(id)
            .map(customerMapper:: toDto);
    }
}
