package com.cliente.cliente_back.services;

import java.util.List;

import com.cliente.cliente_back.dto.RetentionEligibilityResponseDTO;
import com.cliente.cliente_back.dto.RetentionPreviewRequestDTO;
import com.cliente.cliente_back.dto.RetentionPreviewResponseDTO;
import com.cliente.cliente_back.dto.RetentionTierDTO;

public interface RetentionOfferService {

    List<RetentionTierDTO> listTiers();

    RetentionEligibilityResponseDTO getEligibility(Long customerId, Long serviceId);

    RetentionPreviewResponseDTO preview(RetentionPreviewRequestDTO request);
}
