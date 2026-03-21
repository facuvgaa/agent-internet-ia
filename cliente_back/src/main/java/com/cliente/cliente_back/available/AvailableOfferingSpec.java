package com.cliente.cliente_back.available;

import java.util.Arrays;
import java.util.Optional;

/**
 * Catálogo de servicios/adicionales que el cliente puede contratar.
 * La elegibilidad se calcula contando servicios activos del cliente cuyo
 * {@code serviceType} coincide (ignorando mayúsculas) con {@link #existingServiceTypeMatch()}.
 */
public enum AvailableOfferingSpec {

    NETFLIX_PACK(
            "NETFLIX_PACK",
            "Pack Netflix",
            "Streaming bajo demanda incluido en la factura.",
            "Streaming",
            1),
    DISNEY_PACK(
            "DISNEY_PACK",
            "Disney+",
            "Plataforma de streaming Disney+.",
            "Streaming",
            1),
    EXTRA_MOBILE_LINE(
            "EXTRA_MOBILE_LINE",
            "Línea móvil adicional",
            "Segunda o más líneas móviles en la misma cuenta.",
            "Móvil",
            5),
    CLOUD_STORAGE_500(
            "CLOUD_STORAGE_500",
            "Almacenamiento en la nube 500 GB",
            "Respaldo y nube para fotos y archivos.",
            "Nube",
            2),
    STATIC_IP(
            "STATIC_IP",
            "IP fija",
            "IP estática para conexión a Internet.",
            "Internet",
            3);

    private final String code;
    private final String displayName;
    private final String description;
    private final String existingServiceTypeMatch;
    private final int maxActiveOfType;

    AvailableOfferingSpec(
            String code,
            String displayName,
            String description,
            String existingServiceTypeMatch,
            int maxActiveOfType) {
        this.code = code;
        this.displayName = displayName;
        this.description = description;
        this.existingServiceTypeMatch = existingServiceTypeMatch;
        this.maxActiveOfType = maxActiveOfType;
    }

    public String code() {
        return code;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public String existingServiceTypeMatch() {
        return existingServiceTypeMatch;
    }

    public int maxActiveOfType() {
        return maxActiveOfType;
    }

    public static Optional<AvailableOfferingSpec> fromCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(s -> s.code.equalsIgnoreCase(code))
                .findFirst();
    }
}
