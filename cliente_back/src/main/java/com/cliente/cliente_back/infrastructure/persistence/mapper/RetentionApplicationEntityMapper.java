package com.cliente.cliente_back.infrastructure.persistence.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import com.cliente.cliente_back.application.dto.retention.RetentionApplicationResponseDTO;
import com.cliente.cliente_back.domain.model.RetentionApplication;
import com.cliente.cliente_back.infrastructure.persistence.entity.RetentionApplicationEntity;

@Mapper(componentModel = "spring")
public interface RetentionApplicationEntityMapper {

    RetentionApplication toDomain(RetentionApplicationEntity entity);
    RetentionApplicationEntity toEntity(RetentionApplication domain);

    @Mapping(target = "applicationId", source = "id")
    @Mapping(target = "status", expression = "java(entity.getStatus() != null ? entity.getStatus().name() : null)")
    @Mapping(target = "validFrom", expression = "java(entity.getValidFrom() != null ? entity.getValidFrom().toString() : null)")
    @Mapping(target = "validUntil", expression = "java(entity.getValidUntil() != null ? entity.getValidUntil().toString() : null)")
    @Mapping(target = "message", expression = "java(\"Acuerdo de retención registrado con éxito\")")
    RetentionApplicationResponseDTO toDto(RetentionApplicationEntity entity);

    @Mapping(target = "applicationId", source = "id")
    @Mapping(target = "status", expression = "java(domain.getStatus() != null ? domain.getStatus().name() : null)")
    @Mapping(target = "validFrom", expression = "java(domain.getValidFrom() != null ? domain.getValidFrom().toString() : null)")
    @Mapping(target = "validUntil", expression = "java(domain.getValidUntil() != null ? domain.getValidUntil().toString() : null)")
    @Mapping(target = "message", expression = "java(\"Acuerdo de retención registrado con éxito\")")
    RetentionApplicationResponseDTO toDto(RetentionApplication domain);
}
