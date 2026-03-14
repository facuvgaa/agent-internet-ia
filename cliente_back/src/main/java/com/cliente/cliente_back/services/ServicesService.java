package com.cliente.cliente_back.services;

import java.util.List;

import com.cliente.cliente_back.dto.ServicesDTO;

public interface ServicesService {
    List<ServicesDTO> findAllByCustomerId(Long id);
}
