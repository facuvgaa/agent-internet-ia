package com.cliente.cliente_back.infrastructure.web.controller;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cliente.cliente_back.application.dto.retention.RetentionApplicationRequestDTO;
import com.cliente.cliente_back.application.dto.retention.RetentionApplicationResponseDTO;
import com.cliente.cliente_back.application.dto.retention.RetentionEligibilityResponseDTO;
import com.cliente.cliente_back.application.dto.retention.RetentionPreviewRequestDTO;
import com.cliente.cliente_back.application.dto.retention.RetentionPreviewResponseDTO;
import com.cliente.cliente_back.application.dto.retention.RetentionTierDTO;
import com.cliente.cliente_back.domain.port.in.RetentionApplicationUseCase;
import com.cliente.cliente_back.domain.port.in.RetentionOfferUseCase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/retention")
@RequiredArgsConstructor
@Slf4j
public class RetentionController {

    private final RetentionOfferUseCase retentionOfferUseCase;
    private final RetentionApplicationUseCase retentionApplicationUseCase;

    @GetMapping("/tiers")
    public ResponseEntity<List<RetentionTierDTO>> listTiers() {
        return ResponseEntity.ok(retentionOfferUseCase.listTiers());
    }

    @GetMapping("/customers/{customerId}/eligibility")
    public ResponseEntity<RetentionEligibilityResponseDTO> getEligibility(
            @PathVariable Long customerId, @RequestParam(required = false) Long serviceId) {
        return ResponseEntity.ok(retentionOfferUseCase.getEligibility(customerId, serviceId));
    }

    @PostMapping("/preview")
    public ResponseEntity<RetentionPreviewResponseDTO> preview(@RequestBody RetentionPreviewRequestDTO request) {
        try {
            return ResponseEntity.ok(retentionOfferUseCase.preview(request));
        } catch (IllegalArgumentException ex) {
            log.warn("retention preview: {}", ex.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/applications")
    public ResponseEntity<RetentionApplicationResponseDTO> applyRetention(
            @RequestBody RetentionApplicationRequestDTO request) {
        try {
            RetentionApplicationResponseDTO body = retentionApplicationUseCase.apply(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(body);
        } catch (IllegalArgumentException ex) {
            log.warn("retention apply: {}", ex.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
}

