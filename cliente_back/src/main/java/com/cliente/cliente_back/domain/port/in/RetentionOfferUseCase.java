package com.cliente.cliente_back.domain.port.in;

import java.util.List;
import com.cliente.cliente_back.application.dto.retention.RetentionEligibilityResponseDTO;
import com.cliente.cliente_back.application.dto.retention.RetentionPreviewRequestDTO;
import com.cliente.cliente_back.application.dto.retention.RetentionPreviewResponseDTO;
import com.cliente.cliente_back.application.dto.retention.RetentionTierDTO;

public interface RetentionOfferUseCase {
    List<RetentionTierDTO> listTiers();
    RetentionEligibilityResponseDTO getEligibility(Long customerId, Long serviceId);
    RetentionPreviewResponseDTO preview(RetentionPreviewRequestDTO request);
}

