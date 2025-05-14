package com.sg.webhookservice.presentation.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DTO que representa un mensaje de webhook con su información asociada,
 * incluyendo estado, intentos de entrega y tiempos.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Representa un mensaje de webhook y su estado actual")
public class MessageDto {

    @Schema(description = "ID único del mensaje", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID id;

    @Schema(description = "ID de la configuración de webhook asociada", example = "456e7890-e12b-34d5-a678-426614174000")
    private UUID webhookConfigId;

    @Schema(description = "Nombre del webhook asociado", example = "payment-notification")
    private String webhookName;

    @Schema(description = "URL de destino a la que se enviará el webhook", example = "https://example.com/webhook-receiver")
    private String targetUrl;

    @Schema(description = "Estado actual del mensaje", example = "DELIVERED",
            allowableValues = {"PENDING", "PROCESSING", "DELIVERED", "FAILED", "CANCELLED"})
    private String status;

    @Schema(description = "Firma HMAC calculada para este mensaje", example = "sha256=a1b2c3d4e5f6...")
    private String signature;

    @Schema(description = "Cabeceras personalizadas para el envío", example = "{\"X-Custom-Header\": \"value\"}")
    private Map<String, String> headers;

    @Schema(description = "Payload del mensaje (contenido del webhook)", example = "{\"event\": \"payment\", \"status\": \"completed\"}")
    private String payload;

    @Schema(description = "Número de reintentos realizados", example = "2")
    private int retryCount;

    @Schema(description = "Próxima fecha programada para reintento (si aplica)")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
    private OffsetDateTime nextRetry;

    @Schema(description = "Último mensaje de error registrado", example = "Connection timed out")
    private String lastError;

    @Schema(description = "Fecha de creación del mensaje")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
    private OffsetDateTime createdAt;

    @Schema(description = "Fecha de última actualización del mensaje")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
    private OffsetDateTime updatedAt;

    @Schema(description = "Lista de intentos de entrega para este mensaje")
    @Builder.Default
    private List<DeliveryAttemptDto> deliveryAttempts = new ArrayList<>();

    /**
     * Método para determinar si el mensaje está en un estado terminal (no se procesará más)
     *
     * @return true si el mensaje está en un estado terminal
     */
    @Schema(description = "Indica si el mensaje está en un estado terminal (no se procesará más)", accessMode = Schema.AccessMode.READ_ONLY)
    public boolean isTerminalState() {
        return "DELIVERED".equals(status) || "CANCELLED".equals(status) ||
                ("FAILED".equals(status) && nextRetry == null);
    }

    /**
     * Método para determinar si el mensaje puede ser reintentado
     *
     * @return true si el mensaje puede ser reintentado
     */
    @Schema(description = "Indica si el mensaje puede ser reintentado", accessMode = Schema.AccessMode.READ_ONLY)
    public boolean isRetryable() {
        return "FAILED".equals(status) && nextRetry != null;
    }
}
