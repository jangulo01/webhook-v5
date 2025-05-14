package com.sg.webhookservice.presentation.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * DTO para las estadísticas de salud de un webhook.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Estadísticas de salud de un webhook")
public class WebhookHealthStatsDto {

    @Schema(description = "ID único de las estadísticas", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID id;

    @Schema(description = "ID de la configuración de webhook asociada", example = "456e7890-e12b-34d5-a678-426614174000")
    private UUID webhookConfigId;

    @Schema(description = "Nombre del webhook", example = "payment-notification")
    private String webhookName;

    @Schema(description = "Número total de mensajes enviados", example = "150")
    private int totalSent;

    @Schema(description = "Número de mensajes entregados exitosamente", example = "142")
    private int totalDelivered;

    @Schema(description = "Número de mensajes fallidos", example = "8")
    private int totalFailed;

    @Schema(description = "Tiempo de respuesta promedio en milisegundos", example = "245")
    private double avgResponseTime;

    @Schema(description = "Último éxito en la entrega")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
    private OffsetDateTime lastSuccessTime;

    @Schema(description = "Último error en la entrega")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
    private OffsetDateTime lastErrorTime;

    @Schema(description = "Último mensaje de error", example = "Connection refused")
    private String lastError;

    @Schema(description = "Fecha de creación de las estadísticas")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
    private OffsetDateTime createdAt;

    @Schema(description = "Fecha de última actualización de las estadísticas")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
    private OffsetDateTime updatedAt;

    /**
     * Calcula la tasa de éxito basada en las entregas.
     *
     * @return Porcentaje de mensajes entregados exitosamente
     */
    @Schema(description = "Tasa de éxito (porcentaje)", example = "94.6", accessMode = Schema.AccessMode.READ_ONLY)
    public double getSuccessRate() {
        if (totalSent == 0) {
            return 100.0;
        }
        return (double) totalDelivered / totalSent * 100;
    }

    /**
     * Determina el estado de salud basado en la tasa de éxito.
     *
     * @return Estado de salud: HEALTHY, DEGRADED, o UNHEALTHY
     */
    @Schema(description = "Estado de salud", example = "HEALTHY", accessMode = Schema.AccessMode.READ_ONLY,
            allowableValues = {"HEALTHY", "DEGRADED", "UNHEALTHY", "UNKNOWN"})
    public String getHealthStatus() {
        double successRate = getSuccessRate();

        if (totalSent < 5) {
            return "UNKNOWN";
        } else if (successRate >= 95) {
            return "HEALTHY";
        } else if (successRate >= 75) {
            return "DEGRADED";
        } else {
            return "UNHEALTHY";
        }
    }
}
