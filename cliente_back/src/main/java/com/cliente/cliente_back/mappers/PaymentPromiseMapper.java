package com.cliente.cliente_back.mappers;

import org.mapstruct.Mapper;

import com.cliente.cliente_back.dto.PaymentPromiseDTO;
import com.cliente.cliente_back.entities.PaymentPromiseEntity;

@Mapper(componentModel = "spring")
public interface PaymentPromiseMapper {
    PaymentPromiseDTO toDto(PaymentPromiseEntity entity);
    PaymentPromiseEntity toEntity(PaymentPromiseDTO dto);
}
