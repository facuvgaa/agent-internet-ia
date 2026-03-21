package com.cliente.cliente_back.dto;

/** Vista previa de una oferta concreta (nivel 1–4). */
public record RetentionPreviewRequestDTO(Long customerId, Long serviceId, int level) {}
