package com.cliente.cliente_back.infrastructure.persistence.repository;

import com.cliente.cliente_back.infrastructure.persistence.entity.*;
import com.cliente.cliente_back.domain.model.*;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;




public interface MobileTopUpEntityRepository extends JpaRepository<MobileTopUpEntity, Long> {

    Optional<MobileTopUpEntity> findFirstByCustomerIdAndMsisdnAndStatusOrderByCreatedAtDesc(
            Long customerId,
            String msisdn,
            BenefitStatus status);
    boolean existsByTicketId(Long ticketId);
}
