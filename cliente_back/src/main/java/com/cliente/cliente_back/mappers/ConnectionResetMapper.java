package com.cliente.cliente_back.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.cliente.cliente_back.dto.ConnectionResetRequestDTO;
import com.cliente.cliente_back.dto.ConnectionResetResponseDTO;
import com.cliente.cliente_back.entities.ConnectionResetEntity;

@Mapper(componentModel = "spring")
public interface ConnectionResetMapper {

    ConnectionResetResponseDTO toDto(ConnectionResetEntity entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "resetJobId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "message", ignore = true)
    @Mapping(target = "estimatedSeconds", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    ConnectionResetEntity fromRequest(ConnectionResetRequestDTO request);
}
