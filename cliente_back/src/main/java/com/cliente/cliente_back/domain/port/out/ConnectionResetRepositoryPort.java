package com.cliente.cliente_back.domain.port.out;

import java.util.Optional;
import com.cliente.cliente_back.domain.model.ConnectionReset;

public interface ConnectionResetRepositoryPort {
    ConnectionReset save(ConnectionReset reset);
    Optional<ConnectionReset> findById(Long id);
}
