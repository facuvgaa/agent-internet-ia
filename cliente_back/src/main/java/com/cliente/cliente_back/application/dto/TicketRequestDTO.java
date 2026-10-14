package com.cliente.cliente_back.application.dto;

import com.cliente.cliente_back.domain.model.*;


public record TicketRequestDTO(
    Long id,
    Long customerId,
    String subject,
    String priority
) {}
