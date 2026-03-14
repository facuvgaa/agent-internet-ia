package com.cliente.cliente_back.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cliente.cliente_back.entities.ServicesEntity;

public interface ServiceRepository extends JpaRepository <ServicesEntity, Long> {
    List<ServicesEntity> findByCustomer_Id(Long customerId);
}
