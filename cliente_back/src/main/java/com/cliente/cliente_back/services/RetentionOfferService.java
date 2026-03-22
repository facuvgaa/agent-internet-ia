package com.cliente.cliente_back.services;

import java.util.List;

import com.cliente.cliente_back.dto.retention.RetentionEligibilityResponseDTO;
import com.cliente.cliente_back.dto.retention.RetentionPreviewRequestDTO;
import com.cliente.cliente_back.dto.retention.RetentionPreviewResponseDTO;
import com.cliente.cliente_back.dto.retention.RetentionTierDTO;

public interface RetentionOfferService {

    List<RetentionTierDTO> listTiers();

    RetentionEligibilityResponseDTO getEligibility(Long customerId, Long serviceId);

    RetentionPreviewResponseDTO preview(RetentionPreviewRequestDTO request);
}
