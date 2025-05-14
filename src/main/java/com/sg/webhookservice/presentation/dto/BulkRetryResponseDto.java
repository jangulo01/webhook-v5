package com.sg.webhookservice.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
    private int total;

    @Schema(description = "Número de mensajes enviados exitosamente", example = "95")
    private int successful;

    @Schema(description = "Número de mensajes fallidos", example = "5")
    private int failed;

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
            successful++;
        } else {
            failed++;
        }
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
}
