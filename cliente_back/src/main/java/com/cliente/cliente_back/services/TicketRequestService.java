package com.cliente.cliente_back.services;

import com.cliente.cliente_back.dto.TicketRequestDTO;

public interface TicketRequestService {
    TicketRequestDTO createTicket(TicketRequestDTO dto);
}
