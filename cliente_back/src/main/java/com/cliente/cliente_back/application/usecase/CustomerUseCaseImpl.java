package com.cliente.cliente_back.application.usecase;

import java.util.Optional;
import org.springframework.stereotype.Service;
import com.cliente.cliente_back.application.dto.CustomerDTO;
import com.cliente.cliente_back.domain.port.in.CustomerUseCase;
import com.cliente.cliente_back.domain.port.out.CustomerRepositoryPort;
import com.cliente.cliente_back.infrastructure.persistence.mapper.CustomerEntityMapper;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CustomerUseCaseImpl implements CustomerUseCase {

    private final CustomerRepositoryPort customerRepositoryPort;
    private final CustomerEntityMapper customerMapper;

    @Override
    public Optional<CustomerDTO> getCustomerById(Long id) {
        return customerRepositoryPort.findById(id).map(customerMapper::toDto);
    }
}
