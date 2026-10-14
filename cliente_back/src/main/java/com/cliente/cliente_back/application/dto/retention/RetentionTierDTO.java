package com.cliente.cliente_back.application.dto.retention;

import com.cliente.cliente_back.domain.model.*;

public record RetentionTierDTO(int level, int discountPercent, int durationMonths) {}
