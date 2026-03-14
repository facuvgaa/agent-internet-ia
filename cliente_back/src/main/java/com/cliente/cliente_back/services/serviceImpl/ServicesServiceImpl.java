package com.cliente.cliente_back.services.serviceImpl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.cliente.cliente_back.dto.ServicesDTO;
import com.cliente.cliente_back.mappers.ServiceMapper;
import com.cliente.cliente_back.repositories.ServiceRepository;
import com.cliente.cliente_back.services.ServicesService;

import lombok.RequiredArgsConstructor;



@Service
@RequiredArgsConstructor
public class ServicesServiceImpl implements ServicesService {
    private final ServiceRepository serviceRepository;
    private final ServiceMapper serviceMapper;
    @Override
    public List<ServicesDTO> findAllByCustomerId(Long customerId) {
        return serviceRepository.findByCustomer_Id(customerId)
                .stream()
                .map(serviceMapper::toDto)
                .toList();
    }


}
