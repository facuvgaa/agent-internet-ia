package com.cliente.cliente_back.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cliente.cliente_back.entities.ConnectionResetEntity;

public interface ConnectionResetRepository extends JpaRepository<ConnectionResetEntity, Long> {
}
