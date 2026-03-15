package com.cliente.cliente_back.services.serviceImpl;

import org.springframework.stereotype.Service;

import com.cliente.cliente_back.dto.TicketRequestDTO;
import com.cliente.cliente_back.mappers.TicketRequestMapper;
import com.cliente.cliente_back.repositories.TicketRequestRepository;
import com.cliente.cliente_back.services.TicketRequestService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TicketRequestServiceImpl implements TicketRequestService {
    private final TicketRequestRepository ticketRequestRepository;
    private final TicketRequestMapper ticketRequestMapper;

    @Override
    public TicketRequestDTO createTicket(TicketRequestDTO dto){
        var entity = ticketRequestMapper.toEntity(dto);
        var saved = ticketRequestRepository.save(entity);
        return ticketRequestMapper.toDto(saved);
    }

}
