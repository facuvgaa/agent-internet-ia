package com.cliente.cliente_back.application.usecase;

import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import com.cliente.cliente_back.application.dto.TicketRequestDTO;
import com.cliente.cliente_back.domain.model.TicketRequest;
import com.cliente.cliente_back.domain.port.in.TicketRequestUseCase;
import com.cliente.cliente_back.domain.port.out.TicketRequestRepositoryPort;
import com.cliente.cliente_back.infrastructure.persistence.mapper.TicketRequestEntityMapper;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TicketRequestUseCaseImpl implements TicketRequestUseCase {

    private final TicketRequestRepositoryPort ticketRequestRepositoryPort;
    private final TicketRequestEntityMapper ticketRequestMapper;

    @Override
    public TicketRequestDTO createTicket(TicketRequestDTO dto) {
        TicketRequest ticket = TicketRequest.builder()
                .customerId(dto.customerId())
                .subject(dto.subject())
                .priority(dto.priority())
                .status("OPEN")
                .createdAt(LocalDateTime.now())
                .build();
        TicketRequest saved = ticketRequestRepositoryPort.save(ticket);
        return ticketRequestMapper.toDto(saved);
    }
}
