package com.cliente.cliente_back.infrastructure.persistence.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import com.cliente.cliente_back.application.dto.ServicesDTO;
import com.cliente.cliente_back.domain.model.ServiceSubscription;
import com.cliente.cliente_back.infrastructure.persistence.entity.CustomerEntity;
import com.cliente.cliente_back.infrastructure.persistence.entity.ServicesEntity;

@Mapper(componentModel = "spring")
public interface ServiceEntityMapper {

    @Mapping(target = "customerId", source = "customerId.id")
    ServiceSubscription toDomain(ServicesEntity entity);

    @Mapping(target = "customerId", expression = "java(mapCustomer(domain.getCustomerId()))")
    ServicesEntity toEntity(ServiceSubscription domain);

    ServicesDTO toDto(ServiceSubscription domain);
    ServicesDTO toDto(ServicesEntity entity);
    ServicesEntity toEntity(ServicesDTO dto);

    default CustomerEntity mapCustomer(Long customerId) {
        if (customerId == null) {
            return null;
        }
        CustomerEntity c = new CustomerEntity();
        c.setId(customerId);
        return c;
    }
}

