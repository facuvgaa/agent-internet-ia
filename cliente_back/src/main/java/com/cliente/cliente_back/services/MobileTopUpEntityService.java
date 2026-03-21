package com.cliente.cliente_back.services;

import com.cliente.cliente_back.dto.MobileTopUpRequestDTO;
import com.cliente.cliente_back.dto.MobileTopUpResponseDTO;

public interface MobileTopUpEntityService {

    MobileTopUpResponseDTO grantTopUp(MobileTopUpRequestDTO request);
}
