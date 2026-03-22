package com.cliente.cliente_back.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cliente.cliente_back.entities.ServicesEntity;

public interface ServiceRepository extends JpaRepository <ServicesEntity, Long> {
    /** {@link com.cliente.cliente_back.entities.ServicesEntity#getCustomerId()} es el {@code ManyToOne}, no existe propiedad {@code customer}. */
    List<ServicesEntity> findByCustomerId_Id(Long customerId);
}
