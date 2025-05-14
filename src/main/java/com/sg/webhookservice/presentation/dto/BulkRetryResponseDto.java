package com.sg.webhookservice.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DTO para respuestas de operaciones de reintento masivo.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Resultado de una operación de reintento masivo")
public class BulkRetryResponseDto {

    @Schema(description = "Número total de mensajes procesados", example = "100")
    private int processedCount;

    @Schema(description = "Número de mensajes enviados exitosamente", example = "95")
    private int successCount;

    @Schema(description = "Número de mensajes fallidos", example = "5")
    private int failedCount;

    @Schema(description = "IDs de los mensajes que fallaron")
    private List<UUID> failedIds;

    @Schema(description = "Detalles de los mensajes procesados")
    @Builder.Default
    private List<Map<String, Object>> messages = new ArrayList<>();

    /**
     * Agrega un mensaje al resultado.
     *
     * @param messageId ID del mensaje
     * @param webhookName Nombre del webhook
     * @param status Estado resultante
     * @param statusCode Código de estado HTTP (opcional)
     * @param response Respuesta recibida (opcional)
     */
    public void addMessage(String messageId, String webhookName, String status, Integer statusCode, String response) {
        Map<String, Object> message = Map.of(
                "message_id", messageId,
                "webhook_name", webhookName,
                "status", status,
                "status_code", statusCode != null ? statusCode : "",
                "response", response != null ? truncateResponse(response) : ""
        );

        messages.add(message);

        if ("delivered".equals(status)) {
            successCount++;
        } else {
            failedCount++;
        }

        processedCount = successCount + failedCount;
    }

    /**
     * Obtiene la lista de IDs de mensajes que fallaron.
     *
     * @return Lista de UUIDs de mensajes fallidos
     */
    public List<UUID> getFailedIds() {
        return failedIds != null ? failedIds : Collections.emptyList();
    }

    /**
     * Establece la lista de IDs de mensajes que fallaron.
     *
     * @param failedIds Lista de UUIDs
     */
    public void setFailedIds(List<UUID> failedIds) {
        this.failedIds = failedIds;
    }

    /**
     * Trunca una respuesta si es muy larga.
     *
     * @param response Respuesta original
     * @return Respuesta truncada si es necesario
     */
    private String truncateResponse(String response) {
        if (response == null) {
            return null;
        }

        if (response.length() <= 100) {
            return response;
        }

        return response.substring(0, 100) + "...";
    }

    /**
     * Para compatibilidad con versiones anteriores
     */
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public int getTotal() {
        return processedCount;
    }

    /**
     * Para compatibilidad con versiones anteriores
     */
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public int getSuccessful() {
        return successCount;
    }

    /**
     * Para compatibilidad con versiones anteriores
     */
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public int getFailed() {
        return failedCount;
    }
}