package com.cliente.cliente_back.infrastructure.persistence.adapter;

import java.util.Optional;
import org.springframework.stereotype.Component;
import com.cliente.cliente_back.domain.model.TicketRequest;
import com.cliente.cliente_back.domain.port.out.TicketRequestRepositoryPort;
import com.cliente.cliente_back.infrastructure.persistence.mapper.TicketRequestEntityMapper;
import com.cliente.cliente_back.infrastructure.persistence.repository.TicketRequestRepository;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TicketRequestPersistenceAdapter implements TicketRequestRepositoryPort {

    private final TicketRequestRepository ticketRequestRepository;
    private final TicketRequestEntityMapper ticketRequestMapper;

    @Override
    public TicketRequest save(TicketRequest ticket) {
        var entity = ticketRequestMapper.toEntity(ticket);
        var saved = ticketRequestRepository.save(entity);
        return ticketRequestMapper.toDomain(saved);
    }

    @Override
    public Optional<TicketRequest> findById(Long id) {
        return ticketRequestRepository.findById(id).map(ticketRequestMapper::toDomain);
    }
}
