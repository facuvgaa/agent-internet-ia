package com.cliente.cliente_back.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cliente.cliente_back.dto.BenefitStatus;
import com.cliente.cliente_back.entities.MobileTopUpEntity;

public interface MobileTopUpEntityRepository extends JpaRepository<MobileTopUpEntity, Long> {

    Optional<MobileTopUpEntity> findFirstByCustomerIdAndMsisdnAndStatusOrderByCreatedAtDesc(
            Long customerId,
            String msisdn,
            BenefitStatus status);
}
