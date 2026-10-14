package com.cliente.cliente_back.infrastructure.persistence.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import com.cliente.cliente_back.application.dto.NetworkDiagnosticRequestDTO;
import com.cliente.cliente_back.application.dto.NetworkDiagnosticResponseDTO;
import com.cliente.cliente_back.domain.model.NetworkDiagnostic;
import com.cliente.cliente_back.infrastructure.persistence.entity.NetworkDiagnosticEntity;

@Mapper(componentModel = "spring")
public interface NetworkDiagnosticEntityMapper {

    NetworkDiagnostic toDomain(NetworkDiagnosticEntity entity);
    NetworkDiagnosticEntity toEntity(NetworkDiagnostic domain);

    @Mapping(target = "diagnosticId", expression = "java(String.valueOf(entity.getId()))")
    NetworkDiagnosticResponseDTO toDto(NetworkDiagnosticEntity entity);

    @Mapping(target = "diagnosticId", expression = "java(String.valueOf(domain.getId()))")
    NetworkDiagnosticResponseDTO toDto(NetworkDiagnostic domain);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "massiveOutage", ignore = true)
    @Mapping(target = "zoneOrNode", ignore = true)
    @Mapping(target = "etaMinutes", ignore = true)
    @Mapping(target = "canRemoteReset", ignore = true)
    @Mapping(target = "incidentId", ignore = true)
    @Mapping(target = "message", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    NetworkDiagnosticEntity fromRequest(NetworkDiagnosticRequestDTO request);
}
