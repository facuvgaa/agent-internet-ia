package com.cliente.cliente_back.domain.port.out;

import java.util.Optional;
import com.cliente.cliente_back.domain.model.BenefitStatus;
import com.cliente.cliente_back.domain.model.MobileTopUp;

public interface MobileTopUpRepositoryPort {
    MobileTopUp save(MobileTopUp topUp);
    boolean existsByTicketId(Long ticketId);
    Optional<MobileTopUp> findFirstByCustomerIdAndMsisdnAndStatusOrderByCreatedAtDesc(
            Long customerId, String msisdn, BenefitStatus status);
}
