package com.cliente.cliente_back.services.serviceImpl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.cliente.cliente_back.dto.BillingDTO;
import com.cliente.cliente_back.mappers.BillingMapper;
import com.cliente.cliente_back.repositories.BillingRepository;
import com.cliente.cliente_back.services.BillingService;

import lombok.RequiredArgsConstructor;


@Service
@RequiredArgsConstructor
public class BillingServiceImpl implements BillingService{
    private final BillingRepository billingRepository;
    private final BillingMapper billingMapper;

    @Override
    public List<BillingDTO> findAllBilling(Long id){
        return billingRepository.findByCustomerId(id)
            .stream()
            .map(billingMapper::toDto)
            .toList();
    }
}
