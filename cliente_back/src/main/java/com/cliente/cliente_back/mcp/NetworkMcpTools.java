package com.cliente.cliente_back.mcp;

import com.cliente.cliente_back.dto.ConnectionResetRequestDTO;
import com.cliente.cliente_back.dto.ConnectionResetResponseDTO;
import com.cliente.cliente_back.dto.NetworkDiagnosticRequestDTO;
import com.cliente.cliente_back.dto.NetworkDiagnosticResponseDTO;
import com.cliente.cliente_back.services.ConnectionResetService;
import com.cliente.cliente_back.services.NetworkDiagnosticService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NetworkMcpTools {

    private final NetworkDiagnosticService networkDiagnosticService;
    private final ConnectionResetService connectionResetService;

    public NetworkMcpTools(
            NetworkDiagnosticService networkDiagnosticService,
            ConnectionResetService connectionResetService) {
        this.networkDiagnosticService = networkDiagnosticService;
        this.connectionResetService = connectionResetService;
    }

    @Tool(name = "run_network_diagnostic",
          description = "Ejecuta un diagnóstico de red en tiempo real para la línea indicada. Devuelve estado de señal, latencia y problemas detectados.")
    public NetworkDiagnosticResponseDTO runNetworkDiagnostic(
            @ToolParam(description = "ID numérico del cliente") Long customerId,
            @ToolParam(description = "ID de la línea/servicio a diagnosticar") Long serviceId,
            @ToolParam(description = "Canal de origen. Default: IA", required = false) String channel) {
        String resolvedChannel = (channel != null && !channel.isBlank()) ? channel : "IA";
        return networkDiagnosticService.runDiagnostic(
                new NetworkDiagnosticRequestDTO(customerId, serviceId, resolvedChannel));
    }

    @Tool(name = "list_network_diagnostics",
          description = "Lista el historial de diagnósticos de red para una línea, ordenado del más reciente al más antiguo.")
    public List<NetworkDiagnosticResponseDTO> listNetworkDiagnostics(
            @ToolParam(description = "ID numérico del cliente") Long customerId,
            @ToolParam(description = "ID de la línea/servicio") Long serviceId) {
        return networkDiagnosticService.findByCustomerIdAndServiceIdOrderByCreatedAtDesc(customerId, serviceId);
    }

    @Tool(name = "get_latest_network_diagnostic",
          description = "Retorna el diagnóstico de red más reciente para una línea. Más eficiente que list_network_diagnostics cuando solo importa el último estado.")
    public NetworkDiagnosticResponseDTO getLatestNetworkDiagnostic(
            @ToolParam(description = "ID numérico del cliente") Long customerId,
            @ToolParam(description = "ID de la línea/servicio") Long serviceId) {
        return networkDiagnosticService.findFirstByCustomerIdAndServiceIdOrderByCreatedAtDesc(customerId, serviceId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No se encontró diagnóstico para customerId=" + customerId + " serviceId=" + serviceId));
    }

    @Tool(name = "request_connection_reset",
          description = "Encola un reset de conexión (reinicio CPE/señal) para una línea específica del cliente. Usar cuando hay problemas de conectividad reportados.")
    public ConnectionResetResponseDTO requestConnectionReset(
            @ToolParam(description = "ID numérico del cliente") Long customerId,
            @ToolParam(description = "ID de la línea a resetear") Long serviceId,
            @ToolParam(description = "Motivo del reset") String reason) {
        return connectionResetService.requestReset(
                new ConnectionResetRequestDTO(customerId, serviceId, reason));
    }
}
