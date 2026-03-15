package com.cliente.cliente_back.mappers;

import org.mapstruct.Mapper;

import com.cliente.cliente_back.dto.TicketRequestDTO;
import com.cliente.cliente_back.entities.TicketRequestEntity;

@Mapper(componentModel = "spring")
public interface TicketRequestMapper {
    
    TicketRequestDTO toDto (TicketRequestEntity entity);
    TicketRequestEntity toEntity (TicketRequestDTO dto);
}
