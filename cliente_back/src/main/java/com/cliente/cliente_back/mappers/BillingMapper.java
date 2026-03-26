package com.cliente.cliente_back.mappers;

import java.util.Collections;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.cliente.cliente_back.dto.BillingDTO;
import com.cliente.cliente_back.entities.BillingEntity;

@Mapper(componentModel = "spring", imports = Collections.class)
public interface BillingMapper {

    @Mapping(
            target = "customerId",
            expression = "java(entity.getCustomerId() == null ? null : Long.valueOf(entity.getCustomerId()))")
    @Mapping(target = "serviceSummary", expression = "java(Collections.emptyList())")
    BillingDTO toDto(BillingEntity entity);
}
