package com.cliente.cliente_back.domain.port.in;

import com.cliente.cliente_back.application.dto.TicketRequestDTO;

public interface TicketRequestUseCase {
    TicketRequestDTO createTicket(TicketRequestDTO dto);
}
