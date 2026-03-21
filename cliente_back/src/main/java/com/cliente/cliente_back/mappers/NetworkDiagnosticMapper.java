package com.cliente.cliente_back.mappers;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import com.cliente.cliente_back.dto.NetworkDiagnosticRequestDTO;
import com.cliente.cliente_back.dto.NetworkDiagnosticResponseDTO;
import com.cliente.cliente_back.entities.NetworkDiagnosticEntity;

@Mapper(componentModel = "spring")
public interface NetworkDiagnosticMapper {
    @Mapping(
        target = "diagnosticId",
        expression = "java(String.valueOf(entity.getId()))"
    )
    NetworkDiagnosticResponseDTO toDto(NetworkDiagnosticEntity entity);
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