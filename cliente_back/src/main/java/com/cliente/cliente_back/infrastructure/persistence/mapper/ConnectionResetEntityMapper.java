package com.cliente.cliente_back.infrastructure.persistence.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import com.cliente.cliente_back.application.dto.ConnectionResetRequestDTO;
import com.cliente.cliente_back.application.dto.ConnectionResetResponseDTO;
import com.cliente.cliente_back.domain.model.ConnectionReset;
import com.cliente.cliente_back.infrastructure.persistence.entity.ConnectionResetEntity;

@Mapper(componentModel = "spring")
public interface ConnectionResetEntityMapper {

    ConnectionReset toDomain(ConnectionResetEntity entity);
    ConnectionResetEntity toEntity(ConnectionReset domain);

    ConnectionResetResponseDTO toDto(ConnectionResetEntity entity);
    ConnectionResetResponseDTO toDto(ConnectionReset domain);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "resetJobId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "message", ignore = true)
    @Mapping(target = "estimatedSeconds", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    ConnectionResetEntity fromRequest(ConnectionResetRequestDTO request);
}
