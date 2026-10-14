package com.cliente.cliente_back.infrastructure.persistence.repository;

import com.cliente.cliente_back.infrastructure.persistence.entity.*;
import com.cliente.cliente_back.domain.model.*;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;



public interface ServiceRepository extends JpaRepository <ServicesEntity, Long> {
    /** {@link com.cliente.cliente_back.entities.ServicesEntity#getCustomerId()} es el {@code ManyToOne}, no existe propiedad {@code customer}. */
    List<ServicesEntity> findByCustomerId_Id(Long customerId);
}
