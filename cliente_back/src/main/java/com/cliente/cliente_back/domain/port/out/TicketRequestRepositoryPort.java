package com.cliente.cliente_back.domain.port.out;

import java.util.Optional;
import com.cliente.cliente_back.domain.model.TicketRequest;

public interface TicketRequestRepositoryPort {
    TicketRequest save(TicketRequest ticket);
    Optional<TicketRequest> findById(Long id);
}
