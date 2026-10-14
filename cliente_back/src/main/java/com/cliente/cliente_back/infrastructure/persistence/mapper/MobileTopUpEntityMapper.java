package com.cliente.cliente_back.infrastructure.persistence.mapper;

import java.time.LocalDateTime;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import com.cliente.cliente_back.application.dto.MobileTopUpRequestDTO;
import com.cliente.cliente_back.application.dto.MobileTopUpResponseDTO;
import com.cliente.cliente_back.domain.model.MobileTopUp;
import com.cliente.cliente_back.infrastructure.persistence.entity.MobileTopUpEntity;

@Mapper(componentModel = "spring")
public interface MobileTopUpEntityMapper {

    MobileTopUp toDomain(MobileTopUpEntity entity);
    MobileTopUpEntity toEntity(MobileTopUp domain);

    @Mapping(target = "validUntil", source = "validUntil", qualifiedByName = "localDateTimeToIso")
    @Mapping(target = "nextEligibleAt", source = "nextEligibleAt", qualifiedByName = "localDateTimeToIso")
    @Mapping(
            target = "dataGb",
            expression = "java(entity.getDataGb() != null ? entity.getDataGb().intValue() : MobileTopUpEntity.FIXED_DATA_GB)")
    MobileTopUpResponseDTO toDto(MobileTopUpEntity entity);

    @Mapping(target = "validUntil", source = "validUntil", qualifiedByName = "localDateTimeToIso")
    @Mapping(target = "nextEligibleAt", source = "nextEligibleAt", qualifiedByName = "localDateTimeToIso")
    @Mapping(
            target = "dataGb",
            expression = "java(domain.getDataGb() != null ? domain.getDataGb().intValue() : MobileTopUp.FIXED_DATA_GB)")
    MobileTopUpResponseDTO toDto(MobileTopUp domain);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "benefitId", ignore = true)
    @Mapping(target = "dataGb", constant = "10")
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "validUntil", ignore = true)
    @Mapping(target = "nextEligibleAt", ignore = true)
    @Mapping(target = "message", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    MobileTopUpEntity fromRequest(MobileTopUpRequestDTO request);

    @Named("localDateTimeToIso")
    default String localDateTimeToIso(LocalDateTime value) {
        return value == null ? null : value.toString();
    }
}
