package com.cliente.cliente_back.application.usecase;

import java.util.List;
import org.springframework.stereotype.Service;
import com.cliente.cliente_back.application.dto.ServicesDTO;
import com.cliente.cliente_back.domain.port.in.ServicesUseCase;
import com.cliente.cliente_back.domain.port.out.ServiceRepositoryPort;
import com.cliente.cliente_back.infrastructure.persistence.mapper.ServiceEntityMapper;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ServicesUseCaseImpl implements ServicesUseCase {

    private final ServiceRepositoryPort serviceRepositoryPort;
    private final ServiceEntityMapper serviceMapper;

    @Override
    public List<ServicesDTO> findAllByCustomerId(Long customerId) {
        return serviceRepositoryPort.findByCustomerId(customerId).stream()
                .map(serviceMapper::toDto)
                .toList();
    }
}

