package com.cliente.cliente_back.infrastructure.persistence.adapter;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import com.cliente.cliente_back.domain.model.ServiceSubscription;
import com.cliente.cliente_back.domain.port.out.ServiceRepositoryPort;
import com.cliente.cliente_back.infrastructure.persistence.mapper.ServiceEntityMapper;
import com.cliente.cliente_back.infrastructure.persistence.repository.ServiceRepository;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ServicePersistenceAdapter implements ServiceRepositoryPort {

    private final ServiceRepository serviceRepository;
    private final ServiceEntityMapper serviceMapper;

    @Override
    public Optional<ServiceSubscription> findById(Long id) {
        return serviceRepository.findById(id).map(serviceMapper::toDomain);
    }

    @Override
    public List<ServiceSubscription> findByCustomerId(Long customerId) {
        return serviceRepository.findByCustomerId_Id(customerId).stream()
                .map(serviceMapper::toDomain)
                .toList();
    }

    @Override
    public ServiceSubscription save(ServiceSubscription service) {
        var entity = serviceMapper.toEntity(service);
        var saved = serviceRepository.save(entity);
        return serviceMapper.toDomain(saved);
    }

    @Override
    public boolean existsById(Long id) {
        return serviceRepository.existsById(id);
    }
}

