package com.cliente.cliente_back.infrastructure.persistence.mapper;

import org.mapstruct.Mapper;
import com.cliente.cliente_back.application.dto.TicketRequestDTO;
import com.cliente.cliente_back.domain.model.TicketRequest;
import com.cliente.cliente_back.infrastructure.persistence.entity.TicketRequestEntity;

@Mapper(componentModel = "spring")
public interface TicketRequestEntityMapper {
    TicketRequest toDomain(TicketRequestEntity entity);
    TicketRequestEntity toEntity(TicketRequest domain);
    TicketRequestDTO toDto(TicketRequest domain);
    TicketRequestDTO toDto(TicketRequestEntity entity);
    TicketRequestEntity toEntity(TicketRequestDTO dto);
}
