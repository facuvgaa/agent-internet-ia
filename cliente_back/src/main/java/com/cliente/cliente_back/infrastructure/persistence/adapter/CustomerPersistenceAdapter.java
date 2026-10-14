package com.cliente.cliente_back.infrastructure.persistence.adapter;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import com.cliente.cliente_back.domain.model.Customer;
import com.cliente.cliente_back.domain.port.out.CustomerRepositoryPort;
import com.cliente.cliente_back.infrastructure.persistence.mapper.CustomerEntityMapper;
import com.cliente.cliente_back.infrastructure.persistence.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CustomerPersistenceAdapter implements CustomerRepositoryPort {

    private final CustomerRepository customerRepository;
    private final CustomerEntityMapper customerMapper;

    @Override
    public Optional<Customer> findById(Long id) {
        return customerRepository.findById(id).map(customerMapper::toDomain);
    }

    @Override
    public boolean existsById(Long id) {
        return customerRepository.existsById(id);
    }

    @Override
    public Customer save(Customer customer) {
        var entity = customerMapper.toEntity(customer);
        var saved = customerRepository.save(entity);
        return customerMapper.toDomain(saved);
    }

    @Override
    public List<Customer> findAll() {
        return customerRepository.findAll().stream()
                .map(customerMapper::toDomain)
                .toList();
    }
}

