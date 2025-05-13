package com.sg.webhookservice.dto;

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
 * DTO que representa un mensaje de webhook con toda su información
 * asociada, incluyendo estado, intentos de entrega y tiempos.
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
    private Object payload;

    @Schema(description = "Número de reintentos realizados", example = "2")
    private int retryCount;

    @Schema(description = "Próxima fecha programada para reintento (si aplica)")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
    private String nextRetry;

    @Schema(description = "Último mensaje de error registrado", example = "Connection timed out")
    private String lastError;

    @Schema(description = "Fecha de creación del mensaje")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
    private String createdAt;

    @Schema(description = "Fecha de última actualización del mensaje")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
    private String updatedAt;

    @Schema(description = "Lista de intentos de entrega para este mensaje")
    @Builder.Default
    private List<DeliveryAttemptDto> deliveryAttempts = new ArrayList<>();

    @Schema(description = "Información del cliente que envió el webhook (IP, User-Agent, etc.)")
    private Map<String, String> clientInfo;

    @Schema(description = "Duración total del procesamiento (ms)", example = "1250")
    private Long processingDuration;

    @Schema(description = "Metadatos adicionales asociados al mensaje")
    private Map<String, Object> metadata;

    /**
     * Método de conveniencia para agregar un intento de entrega
     */
    public void addDeliveryAttempt(DeliveryAttemptDto attempt) {
        if (this.deliveryAttempts == null) {
            this.deliveryAttempts = new ArrayList<>();
        }
        this.deliveryAttempts.add(attempt);
    }

    /**
     * Método para convertir OffsetDateTime a String en formato ISO
     */
    public static String formatDateTime(OffsetDateTime dateTime) {
        return dateTime != null ? dateTime.toString() : null;
    }

    /**
     * Devuelve si el mensaje está en un estado terminal (no se procesará más)
     */
    @Schema(description = "Indica si el mensaje está en un estado terminal (no se procesará más)", accessMode = Schema.AccessMode.READ_ONLY)
    public boolean isTerminalState() {
        return "DELIVERED".equals(status) || "CANCELLED".equals(status) || 
               ("FAILED".equals(status) && nextRetry == null);
    }

    /**
     * Devuelve si el mensaje es reintentable
     */
    @Schema(description = "Indica si el mensaje puede ser reintentado", accessMode = Schema.AccessMode.READ_ONLY)
    public boolean isRetryable() {
        return "FAILED".equals(status);
    }

    /**
     * Método de conveniencia para obtener el último intento de entrega
     */
    @Schema(description = "Último intento de entrega registrado", accessMode = Schema.AccessMode.READ_ONLY)
    public DeliveryAttemptDto getLastAttempt() {
        if (deliveryAttempts == null || deliveryAttempts.isEmpty()) {
            return null;
        }
        return deliveryAttempts.get(deliveryAttempts.size() - 1);
    }

    /**
     * Método para obtener el tiempo total transcurrido desde la creación
     */
    @Schema(description = "Tiempo total transcurrido desde la creación (ms)", accessMode = Schema.AccessMode.READ_ONLY)
    public Long getElapsedTimeMs() {
        if (createdAt == null) {
            return null;
        }
        
        OffsetDateTime created;
        try {
            created = OffsetDateTime.parse(createdAt);
        } catch (Exception e) {
            return null;
        }
        
        return java.time.Duration.between(created, OffsetDateTime.now()).toMillis();
    }
}