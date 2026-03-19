package com.cliente.cliente_back.mappers;

import org.mapstruct.Mapper;

import com.cliente.cliente_back.dto.BillingDTO;
import com.cliente.cliente_back.entities.BillingEntity;

@Mapper(componentModel = "spring")
public interface BillingMapper {
    

    BillingDTO toDto (BillingEntity entity);
    BillingEntity toEntity (BillingDTO dto);
}
