package com.cliente.cliente_back.mappers;

import org.mapstruct.Mapper;

import com.cliente.cliente_back.dto.ServicesDTO;
import com.cliente.cliente_back.entities.ServicesEntity;

@Mapper(componentModel = "spring")
public interface ServiceMapper {
    
    ServicesDTO toDto(ServicesEntity entity);
    ServicesEntity toEntity(ServicesDTO dto);
}
