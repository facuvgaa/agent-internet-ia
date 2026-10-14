package com.cliente.cliente_back.infrastructure.mcp;

import java.util.List;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import com.cliente.cliente_back.application.dto.retention.RetentionApplicationRequestDTO;
import com.cliente.cliente_back.application.dto.retention.RetentionApplicationResponseDTO;
import com.cliente.cliente_back.application.dto.retention.RetentionEligibilityResponseDTO;
import com.cliente.cliente_back.application.dto.retention.RetentionPreviewRequestDTO;
import com.cliente.cliente_back.application.dto.retention.RetentionPreviewResponseDTO;
import com.cliente.cliente_back.application.dto.retention.RetentionTierDTO;
import com.cliente.cliente_back.domain.port.in.RetentionApplicationUseCase;
import com.cliente.cliente_back.domain.port.in.RetentionOfferUseCase;

@Service
public class RetentionMcpTools {

    private final RetentionOfferUseCase retentionOfferUseCase;
    private final RetentionApplicationUseCase retentionApplicationUseCase;

    public RetentionMcpTools(
            RetentionOfferUseCase retentionOfferUseCase,
            RetentionApplicationUseCase retentionApplicationUseCase) {
        this.retentionOfferUseCase = retentionOfferUseCase;
        this.retentionApplicationUseCase = retentionApplicationUseCase;
    }

    @Tool(name = "get_retention_tiers",
          description = "Retorna el catálogo de niveles de retención disponibles (niveles 1–4 con porcentaje de descuento y duración en meses).")
    public List<RetentionTierDTO> getRetentionTiers() {
        return retentionOfferUseCase.listTiers();
    }

    @Tool(name = "get_retention_eligibility",
          description = "Verifica si el cliente puede acceder a promociones de retención y qué niveles le aplican. Siempre llamar antes de generar una oferta.")
    public RetentionEligibilityResponseDTO getRetentionEligibility(
            @ToolParam(description = "ID numérico del cliente") Long customerId,
            @ToolParam(description = "ID de línea específica (opcional)", required = false) Long serviceId) {
        return retentionOfferUseCase.getEligibility(customerId, serviceId);
    }

    @Tool(name = "get_retention_preview",
          description = "Simula el precio con descuento de retención para un nivel dado (1–4). No guarda nada — solo para mostrarle la oferta al cliente antes de que acepte.")
    public RetentionPreviewResponseDTO getRetentionPreview(
            @ToolParam(description = "ID numérico del cliente") Long customerId,
            @ToolParam(description = "ID de la línea/servicio") Long serviceId,
            @ToolParam(description = "Nivel de retención a simular (1 a 4)") Integer level) {
        return retentionOfferUseCase.preview(new RetentionPreviewRequestDTO(customerId, serviceId, level));
    }

    @Tool(name = "apply_retention_agreement",
          description = "Registra definitivamente el acuerdo de retención después de que el cliente aceptó la oferta. Persistente — genera descuento real en la cuenta.")
    public RetentionApplicationResponseDTO applyRetentionAgreement(
            @ToolParam(description = "ID numérico del cliente") Long customerId,
            @ToolParam(description = "ID de la línea/servicio") Long serviceId,
            @ToolParam(description = "Nivel de retención aceptado (1 a 4)") Integer level,
            @ToolParam(description = "Clave de idempotencia (opcional)", required = false) String idempotencyKey,
            @ToolParam(description = "Canal de origen. Default: IA", required = false) String channel,
            @ToolParam(description = "Versión de términos aceptados (opcional)", required = false) String acceptedTermsVersion,
            @ToolParam(description = "Notas adicionales (opcional)", required = false) String notes) {
        return retentionApplicationUseCase.apply(
                new RetentionApplicationRequestDTO(
                        customerId,
                        serviceId,
                        level,
                        idempotencyKey,
                        channel,
                        acceptedTermsVersion,
                        notes));
    }
}
