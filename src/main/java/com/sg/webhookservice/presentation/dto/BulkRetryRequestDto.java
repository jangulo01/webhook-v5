package com.sg.webhookservice.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para solicitudes de reintento masivo.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Solicitud para reintentar envíos fallidos")
public class BulkRetryRequestDto {

    @Min(value = 1, message = "El período debe ser al menos 1 hora")
    @Max(value = 720, message = "El período no puede superar 30 días (720 horas)")
    @Schema(description = "Período de tiempo en horas para buscar mensajes fallidos", example = "24", defaultValue = "24")
    private Integer hours;

    @Schema(description = "URL de destino personalizada a usar para los reintentos (opcional)", example = "https://alt-endpoint.example.com/webhook")
    private String destinationUrl;

    @Min(value = 1, message = "El límite debe ser al menos 1")
    @Max(value = 1000, message = "El límite no puede superar 1000")
    @Schema(description = "Límite máximo de mensajes a reintentar", example = "100", defaultValue = "100")
    private Integer limit;

    /**
     * Establece valores predeterminados si no se proporcionan.
     */
    public void setDefaultsIfNull() {
        if (hours == null) hours = 24;
        if (limit == null) limit = 100;
    }
}