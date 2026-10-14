package com.cliente.cliente_back.infrastructure.persistence.mapper;

import org.mapstruct.Mapper;
import com.cliente.cliente_back.application.dto.CustomerDTO;
import com.cliente.cliente_back.domain.model.Customer;
import com.cliente.cliente_back.infrastructure.persistence.entity.CustomerEntity;

@Mapper(componentModel = "spring")
public interface CustomerEntityMapper {
    Customer toDomain(CustomerEntity entity);
    CustomerEntity toEntity(Customer domain);
    CustomerDTO toDto(Customer domain);
    CustomerDTO toDto(CustomerEntity entity);
    CustomerEntity toEntity(CustomerDTO dto);
}

