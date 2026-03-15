package com.cliente.cliente_back.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cliente.cliente_back.entities.TicketRequestEntity;

public interface TicketRequestRepository extends JpaRepository<TicketRequestEntity, Long> {
    
}
