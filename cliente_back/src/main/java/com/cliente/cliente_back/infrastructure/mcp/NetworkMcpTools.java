package com.cliente.cliente_back.infrastructure.mcp;

import java.util.List;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import com.cliente.cliente_back.application.dto.ConnectionResetRequestDTO;
import com.cliente.cliente_back.application.dto.ConnectionResetResponseDTO;
import com.cliente.cliente_back.application.dto.NetworkDiagnosticRequestDTO;
import com.cliente.cliente_back.application.dto.NetworkDiagnosticResponseDTO;
import com.cliente.cliente_back.domain.port.in.ConnectionResetUseCase;
import com.cliente.cliente_back.domain.port.in.NetworkDiagnosticUseCase;

@Service
public class NetworkMcpTools {

    private final NetworkDiagnosticUseCase networkDiagnosticUseCase;
    private final ConnectionResetUseCase connectionResetUseCase;

    public NetworkMcpTools(
            NetworkDiagnosticUseCase networkDiagnosticUseCase,
            ConnectionResetUseCase connectionResetUseCase) {
        this.networkDiagnosticUseCase = networkDiagnosticUseCase;
        this.connectionResetUseCase = connectionResetUseCase;
    }

    @Tool(name = "run_network_diagnostic",
          description = "Ejecuta un diagnóstico de red en tiempo real para la línea indicada. Devuelve estado de señal, latencia y problemas detectados.")
    public NetworkDiagnosticResponseDTO runNetworkDiagnostic(
            @ToolParam(description = "ID numérico del cliente") Long customerId,
            @ToolParam(description = "ID de la línea/servicio a diagnosticar") Long serviceId,
            @ToolParam(description = "Canal de origen. Default: IA", required = false) String channel) {
        String resolvedChannel = (channel != null && !channel.isBlank()) ? channel : "IA";
        return networkDiagnosticUseCase.runDiagnostic(
                new NetworkDiagnosticRequestDTO(customerId, serviceId, resolvedChannel));
    }

    @Tool(name = "list_network_diagnostics",
          description = "Lista el historial de diagnósticos de red para una línea, ordenado del más reciente al más antiguo.")
    public List<NetworkDiagnosticResponseDTO> listNetworkDiagnostics(
            @ToolParam(description = "ID numérico del cliente") Long customerId,
            @ToolParam(description = "ID de la línea/servicio") Long serviceId) {
        return networkDiagnosticUseCase.findByCustomerIdAndServiceIdOrderByCreatedAtDesc(customerId, serviceId);
    }

    @Tool(name = "get_latest_network_diagnostic",
          description = "Retorna el diagnóstico de red más reciente para una línea. Más eficiente que list_network_diagnostics cuando solo importa el último estado.")
    public NetworkDiagnosticResponseDTO getLatestNetworkDiagnostic(
            @ToolParam(description = "ID numérico del cliente") Long customerId,
            @ToolParam(description = "ID de la línea/servicio") Long serviceId) {
        return networkDiagnosticUseCase.findFirstByCustomerIdAndServiceIdOrderByCreatedAtDesc(customerId, serviceId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No se encontró diagnóstico para customerId=" + customerId + " serviceId=" + serviceId));
    }

    @Tool(name = "request_connection_reset",
          description = "Encola un reset de conexión (reinicio CPE/señal) para una línea específica del cliente. Usar cuando hay problemas de conectividad reportados.")
    public ConnectionResetResponseDTO requestConnectionReset(
            @ToolParam(description = "ID numérico del cliente") Long customerId,
            @ToolParam(description = "ID de la línea a resetear") Long serviceId,
            @ToolParam(description = "Motivo del reset") String reason) {
        return connectionResetUseCase.requestReset(
                new ConnectionResetRequestDTO(customerId, serviceId, reason));
    }
}
