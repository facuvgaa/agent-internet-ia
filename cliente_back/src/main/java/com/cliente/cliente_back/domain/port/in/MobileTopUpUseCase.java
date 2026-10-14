package com.cliente.cliente_back.domain.port.in;

import com.cliente.cliente_back.application.dto.MobileTopUpRequestDTO;
import com.cliente.cliente_back.application.dto.MobileTopUpResponseDTO;

public interface MobileTopUpUseCase {
    MobileTopUpResponseDTO grantTopUp(MobileTopUpRequestDTO request);
}
