package com.cliente.cliente_back.infrastructure.persistence.mapper;

import org.mapstruct.Mapper;
import com.cliente.cliente_back.application.dto.PaymentPromiseDTO;
import com.cliente.cliente_back.domain.model.PaymentPromise;
import com.cliente.cliente_back.infrastructure.persistence.entity.PaymentPromiseEntity;

@Mapper(componentModel = "spring")
public interface PaymentPromiseEntityMapper {
    PaymentPromise toDomain(PaymentPromiseEntity entity);
    PaymentPromiseEntity toEntity(PaymentPromise domain);
    PaymentPromiseDTO toDto(PaymentPromise domain);
    PaymentPromiseDTO toDto(PaymentPromiseEntity entity);
    PaymentPromiseEntity toEntity(PaymentPromiseDTO dto);
    PaymentPromise toDomain(PaymentPromiseDTO dto);
}

