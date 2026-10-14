package com.cliente.cliente_back.domain.port.out;

import java.util.List;
import java.util.Optional;
import com.cliente.cliente_back.domain.model.Customer;

public interface CustomerRepositoryPort {
    Optional<Customer> findById(Long id);
    boolean existsById(Long id);
    Customer save(Customer customer);
    List<Customer> findAll();
}

