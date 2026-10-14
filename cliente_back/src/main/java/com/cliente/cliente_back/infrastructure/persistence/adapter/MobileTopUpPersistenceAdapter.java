package com.cliente.cliente_back.infrastructure.persistence.adapter;

import java.util.Optional;
import org.springframework.stereotype.Component;
import com.cliente.cliente_back.domain.model.BenefitStatus;
import com.cliente.cliente_back.domain.model.MobileTopUp;
import com.cliente.cliente_back.domain.port.out.MobileTopUpRepositoryPort;
import com.cliente.cliente_back.infrastructure.persistence.mapper.MobileTopUpEntityMapper;
import com.cliente.cliente_back.infrastructure.persistence.repository.MobileTopUpEntityRepository;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MobileTopUpPersistenceAdapter implements MobileTopUpRepositoryPort {

    private final MobileTopUpEntityRepository mobileTopUpEntityRepository;
    private final MobileTopUpEntityMapper mobileTopUpEntityMapper;

    @Override
    public MobileTopUp save(MobileTopUp topUp) {
        var entity = mobileTopUpEntityMapper.toEntity(topUp);
        var saved = mobileTopUpEntityRepository.save(entity);
        return mobileTopUpEntityMapper.toDomain(saved);
    }

    @Override
    public boolean existsByTicketId(Long ticketId) {
        return mobileTopUpEntityRepository.existsByTicketId(ticketId);
    }

    @Override
    public Optional<MobileTopUp> findFirstByCustomerIdAndMsisdnAndStatusOrderByCreatedAtDesc(
            Long customerId, String msisdn, BenefitStatus status) {
        return mobileTopUpEntityRepository.findFirstByCustomerIdAndMsisdnAndStatusOrderByCreatedAtDesc(customerId, msisdn, status)
                .map(mobileTopUpEntityMapper::toDomain);
    }
}
