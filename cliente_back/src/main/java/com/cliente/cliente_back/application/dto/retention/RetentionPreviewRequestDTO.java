package com.cliente.cliente_back.application.dto.retention;

import com.cliente.cliente_back.domain.model.*;

public record RetentionPreviewRequestDTO(Long customerId, Long serviceId, int level) {}
