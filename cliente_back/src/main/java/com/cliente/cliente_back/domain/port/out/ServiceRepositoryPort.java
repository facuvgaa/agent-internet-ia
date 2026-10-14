package com.cliente.cliente_back.domain.port.out;

import java.util.List;
import java.util.Optional;
import com.cliente.cliente_back.domain.model.ServiceSubscription;

public interface ServiceRepositoryPort {
    Optional<ServiceSubscription> findById(Long id);
    List<ServiceSubscription> findByCustomerId(Long customerId);
    ServiceSubscription save(ServiceSubscription service);
    boolean existsById(Long id);
}

