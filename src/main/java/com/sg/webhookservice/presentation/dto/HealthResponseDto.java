package com.sg.webhookservice.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para respuestas de verificación de salud del servicio.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Información de salud del servicio de webhooks")
public class HealthResponseDto {

    @Schema(description = "Estado general de salud del servicio", example = "healthy",
            allowableValues = {"healthy", "degraded", "unhealthy"})
    private String status;

    @Schema(description = "Versión del servicio", example = "1.1.0")
    private String version;

    @Schema(description = "Información de conexión a la base de datos", example = "localhost:5432/webhook_db")
    private String database;

    @Schema(description = "Esquema de base de datos utilizado", example = "webhook_db")
    private String schema;

    @Schema(description = "Información de conexión a Kafka", example = "localhost:9092")
    private String kafka;

    @Schema(description = "Modo de operación", example = "kafka", allowableValues = {"kafka", "direct"})
    private String mode;

    @Schema(description = "URL de destino de override (si está configurada)", example = "https://alternate-destination.example.com")
    private String destinationUrlOverride;

    @Schema(description = "Estado del módulo HMAC", example = "available", allowableValues = {"available", "unavailable"})
    private String hmacModule;

    @Schema(description = "Número de mensajes pendientes", example = "42")
    private Integer pendingMessages;

    @Schema(description = "Número de webhooks activos", example = "10")
    private Integer activeWebhooks;

    @Schema(description = "Tiempo de actividad en horas", example = "240.5")
    private Double uptime;

    /**
     * Establece el estado basado en las condiciones.
     */
    public void calculateStatus() {
        if ("unavailable".equals(kafka) || "unavailable".equals(database)) {
            this.status = "unhealthy";
        } else if (pendingMessages != null && pendingMessages > 1000) {
            this.status = "degraded";
        } else {
            this.status = "healthy";
        }
    }
}