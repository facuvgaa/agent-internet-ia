package com.cliente.cliente_back.infrastructure.persistence.mapper;

import java.util.Collections;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import com.cliente.cliente_back.application.dto.BillingDTO;
import com.cliente.cliente_back.domain.model.Billing;
import com.cliente.cliente_back.infrastructure.persistence.entity.BillingEntity;

@Mapper(componentModel = "spring", imports = Collections.class)
public interface BillingEntityMapper {

    @Mapping(
            target = "customerId",
            expression = "java(entity.getCustomerId() == null ? null : Long.valueOf(entity.getCustomerId()))")
    Billing toDomain(BillingEntity entity);

    @Mapping(
            target = "customerId",
            expression = "java(domain.getCustomerId() == null ? null : String.valueOf(domain.getCustomerId()))")
    BillingEntity toEntity(Billing domain);

    @Mapping(target = "serviceSummary", expression = "java(Collections.emptyList())")
    BillingDTO toDto(Billing domain);

    @Mapping(
            target = "customerId",
            expression = "java(entity.getCustomerId() == null ? null : Long.valueOf(entity.getCustomerId()))")
    @Mapping(target = "serviceSummary", expression = "java(Collections.emptyList())")
    BillingDTO toDto(BillingEntity entity);
}
