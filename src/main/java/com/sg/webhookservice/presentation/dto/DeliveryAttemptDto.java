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
 * DTO que representa un intento de entrega de un mensaje de webhook.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Representa un intento de entrega de webhook")
public class DeliveryAttemptDto {

    @Schema(description = "ID único del intento de entrega", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID id;

    @Schema(description = "ID del mensaje asociado", example = "456e7890-e12b-34d5-a678-426614174000")
    private UUID messageId;

    @Schema(description = "Número de intento (1 para el primer intento, 2 para el primer reintento, etc.)", example = "2")
    private int attemptNumber;

    @Schema(description = "Momento en que se realizó el intento")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
    private OffsetDateTime timestamp;

    @Schema(description = "Código de estado HTTP recibido (null si hubo error de conexión)", example = "200")
    private Integer statusCode;

    @Schema(description = "Cuerpo de la respuesta recibida (truncado)", example = "{\"status\":\"success\"}")
    private String responseBody;

    @Schema(description = "Mensaje de error en caso de fallo", example = "Connection timeout")
    private String error;

    @Schema(description = "Duración de la solicitud en milisegundos", example = "354")
    private Long requestDuration;

    /**
     * Determina si el intento fue exitoso basado en el código de estado
     *
     * @return true si el intento fue exitoso (código 2xx)
     */
    @Schema(description = "Indica si el intento fue exitoso", accessMode = Schema.AccessMode.READ_ONLY)
    public boolean isSuccessful() {
        return statusCode != null && statusCode >= 200 && statusCode < 300;
    }

    /**
     * Determina si hubo un error de cliente (4xx)
     *
     * @return true si fue un error del cliente
     */
    @Schema(description = "Indica si hubo un error del cliente (4xx)", accessMode = Schema.AccessMode.READ_ONLY)
    public boolean isClientError() {
        return statusCode != null && statusCode >= 400 && statusCode < 500;
    }

    /**
     * Determina si hubo un error de servidor (5xx)
     *
     * @return true si fue un error del servidor
     */
    @Schema(description = "Indica si hubo un error del servidor (5xx)", accessMode = Schema.AccessMode.READ_ONLY)
    public boolean isServerError() {
        return statusCode != null && statusCode >= 500 && statusCode < 600;
    }

    /**
     * Determina si hubo un error de conexión
     *
     * @return true si hubo un error de conexión
     */
    @Schema(description = "Indica si hubo un error de conexión", accessMode = Schema.AccessMode.READ_ONLY)
    public boolean isConnectionError() {
        return statusCode == null && error != null;
    }
}