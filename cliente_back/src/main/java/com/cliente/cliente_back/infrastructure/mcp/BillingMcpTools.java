package com.cliente.cliente_back.infrastructure.mcp;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import com.cliente.cliente_back.application.dto.BillingDTO;
import com.cliente.cliente_back.application.dto.PaymentPromiseDTO;
import com.cliente.cliente_back.domain.port.in.BillingUseCase;
import com.cliente.cliente_back.domain.port.in.PaymentPromiseUseCase;

@Service
public class BillingMcpTools {

    private final BillingUseCase billingUseCase;
    private final PaymentPromiseUseCase paymentPromiseUseCase;

    public BillingMcpTools(BillingUseCase billingUseCase, PaymentPromiseUseCase paymentPromiseUseCase) {
        this.billingUseCase = billingUseCase;
        this.paymentPromiseUseCase = paymentPromiseUseCase;
    }

    @Tool(name = "billing_info",
          description = "Retorna todas las facturas del cliente (estado, monto, fecha vencimiento, número de factura). Usar para consultas de cuenta corriente o identificar facturas impagas.")
    public List<BillingDTO> billingInfo(
            @ToolParam(description = "ID numérico del cliente") Long customerId) {
        return billingUseCase.findAllBilling(customerId);
    }

    @Tool(name = "billing_lookup",
          description = "Busca una factura específica del cliente por número de factura impreso o id. Útil cuando el cliente menciona un número de comprobante o una foto de factura.")
    public BillingDTO billingLookup(
            @ToolParam(description = "ID numérico del cliente") Long customerId,
            @ToolParam(description = "Número de factura o período") String invoiceNumber) {
        return billingUseCase.findBillingByCustomerAndInvoiceNumber(customerId, invoiceNumber)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Factura no encontrada para cliente=" + customerId + " invoiceNumber=" + invoiceNumber));
    }

    @Tool(name = "payment_promises",
          description = "Registra un compromiso de pago sobre una factura impaga. Permite al cliente comprometerse a pagar hasta una fecha futura sin corte de servicio.")
    public PaymentPromiseDTO paymentPromises(
            @ToolParam(description = "ID numérico del cliente") Long customerId,
            @ToolParam(description = "ID de la factura (obtenido de billing_info)") Long billingId,
            @ToolParam(description = "Fecha ISO-8601 límite de pago. Ej: 2025-09-30T23:59:59") String promiseUntil) {
        LocalDateTime parsedDate = LocalDateTime.parse(promiseUntil);
        return paymentPromiseUseCase.createPromise(
                new PaymentPromiseDTO(null, customerId, billingId, parsedDate, null));
    }
}
