package com.sg.webhookservice.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * DTO para solicitudes de operaciones de reintento masivo.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Solicitud para una operación de reintento masivo")
public class BulkRetryRequestDto {

    @Schema(
            description = "Lista de IDs de mensajes a reintentar. Si es null, se reintentarán todos los mensajes fallidos dentro del rango de tiempo.",
            example = "[\"123e4567-e89b-12d3-a456-426614174000\", \"123e4567-e89b-12d3-a456-426614174001\"]"
    )
    private List<UUID> messageIds;

    @Schema(
            description = "Rango de tiempo en horas para buscar mensajes fallidos (se usa solo si messageIds es null).",
            example = "24",
            defaultValue = "24"
    )
    private Integer timeRangeHours;

    @Schema(
            description = "Nombre del webhook para filtrar los mensajes (opcional).",
            example = "payment-notification"
    )
    private String webhookName;

    @Schema(
            description = "Número máximo de mensajes a reintentar (opcional).",
            example = "100"
    )
    private Integer maxMessages;

    @Schema(
            description = "Indica si se debe requerir intentos de reintento para mensajes con errores permanentes.",
            example = "false",
            defaultValue = "false"
    )
    private Boolean forceRetry;

    /**
     * Obtiene la lista de IDs de mensajes a reintentar.
     *
     * @return Lista de UUIDs o null si no se especificaron
     */
    public List<UUID> getMessageIds() {
        return messageIds;
    }

    /**
     * Establece la lista de IDs de mensajes a reintentar.
     *
     * @param messageIds Lista de UUIDs
     */
    public void setMessageIds(List<UUID> messageIds) {
        this.messageIds = messageIds;
    }

    /**
     * Obtiene el rango de tiempo en horas para buscar mensajes fallidos.
     * Usado solo cuando messageIds es null.
     *
     * @return Rango de tiempo en horas, 24 por defecto si no se especifica
     */
    public Integer getTimeRangeHours() {
        return timeRangeHours != null ? timeRangeHours : 24;
    }

    /**
     * Verifica si la solicitud contiene IDs específicos.
     *
     * @return true si se especificaron IDs, false si se usará un rango de tiempo
     */
    @Schema(hidden = true)
    public boolean hasSpecificIds() {
        return messageIds != null && !messageIds.isEmpty();
    }

    /**
     * Verifica si se deben forzar los reintentos para mensajes con errores permanentes.
     *
     * @return true si se deben forzar los reintentos
     */
    public boolean isForceRetry() {
        return Boolean.TRUE.equals(forceRetry);
    }
}