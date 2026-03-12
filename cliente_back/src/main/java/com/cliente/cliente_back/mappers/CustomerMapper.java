package com.cliente.cliente_back.mappers;

import org.mapstruct.Mapper;

import com.cliente.cliente_back.dto.CustomerDTO;
import com.cliente.cliente_back.entities.CustomerEntity;

@Mapper(componentModel = "spring")
public interface CustomerMapper {
    
    CustomerDTO toDto(CustomerEntity entity);
    CustomerEntity toEntity(CustomerDTO dto);
}
