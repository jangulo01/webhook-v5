package com.sg.webhookservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO que representa una solicitud de reintento para mensajes fallidos.
 * Utilizado para procesar reintentos manuales o programados de webhooks.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Solicitud para reintentar el envío de un mensaje de webhook")
public class RetryRequestDto {

    @Schema(description = "ID único del mensaje a reintentar", example = "123e4567-e89b-12d3-a456-426614174000")
    private String messageId;

    @Schema(description = "URL de destino alternativa para este reintento", example = "https://example.com/alternate-webhook")
    @Pattern(regexp = "^https?://.*", message = "La URL debe comenzar con http:// o https://")
    private String destinationUrl;

    @Schema(description = "Secreto alternativo para firmar este reintento", example = "alternate-secret-key")
    private String secret;

    @Schema(description = "Indica si se debe forzar el reintento aunque esté en estado terminal", example = "true", defaultValue = "false")
    private Boolean force;

    @Schema(description = "Prioridad para este reintento (mayor número = mayor prioridad)", example = "10", defaultValue = "5")
    @Min(value = 1, message = "La prioridad debe ser al menos 1")
    @Max(value = 10, message = "La prioridad no puede ser mayor a 10")
    private Integer priority;

    @Schema(description = "Tiempo máximo de espera para la respuesta en milisegundos", example = "15000", defaultValue = "10000")
    @Min(value = 1000, message = "El timeout debe ser al menos 1000 ms")
    @Max(value = 60000, message = "El timeout no puede exceder 60000 ms (1 minuto)")
    private Integer timeout;

    @Schema(description = "Cabeceras HTTP adicionales para este reintento", example = "{\"X-Custom-Header\": \"value\"}")
    private Object headers;

    @Schema(description = "Identificador de la solicitud de reintento para seguimiento", example = "manual-retry-001")
    private String requestId;

    @Schema(description = "Indicador para usar un modo de entrega directo (sin Kafka)", example = "true", defaultValue = "false")
    private Boolean directMode;

    /**
     * Método auxiliar para verificar si todos los campos opcionales son nulos
     * @return true si no se especificó ninguna personalización para el reintento
     */
    @Schema(description = "Indica si no hay personalizaciones para este reintento", accessMode = Schema.AccessMode.READ_ONLY)
    public boolean hasNoCustomizations() {
        return destinationUrl == null &&
                secret == null &&
                (force == null || !force) &&
                priority == null &&
                timeout == null &&
                headers == null &&
                (directMode == null || !directMode);
    }

    /**
     * Método auxiliar para validar la combinación de campos
     * @return true si la solicitud es válida, false si contiene campos incompatibles
     */
    @Schema(description = "Indica si la solicitud tiene una configuración válida", accessMode = Schema.AccessMode.READ_ONLY)
    public boolean isValid() {
        // Siempre debe tener un messageId
        if (messageId == null || messageId.isEmpty()) {
            return false;
        }

        // Si tiene destinationUrl, debe ser una URL válida
        if (destinationUrl != null &&
                !destinationUrl.startsWith("http://") &&
                !destinationUrl.startsWith("https://")) {
            return false;
        }

        // Si tiene secret, debe tener al menos 8 caracteres
        if (secret != null && secret.length() < 8) {
            return false;
        }

        // Todos los demás campos son opcionales y no tienen restricciones de combinación
        return true;
    }

    /**
     * Establece valores predeterminados para campos no especificados
     */
    public void setDefaultsIfNull() {
        if (force == null) force = false;
        if (priority == null) priority = 5;
        if (timeout == null) timeout = 10000;
        if (directMode == null) directMode = false;

        // Generar un requestId aleatorio si no se proporciona
        if (requestId == null || requestId.isEmpty()) {
            requestId = "retry-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        }
    }

    /**
     * Construye un DTO para un reintento simple con valores predeterminados
     *
     * @param messageId ID del mensaje a reintentar
     * @return DTO configurado con valores predeterminados
     */
    public static RetryRequestDto simpleRetry(String messageId) {
        RetryRequestDto dto = new RetryRequestDto();
        dto.setMessageId(messageId);
        dto.setDefaultsIfNull();
        return dto;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public @Pattern(regexp = "^https?://.*", message = "La URL debe comenzar con http:// o https://") String getDestinationUrl() {
        return destinationUrl;
    }

    public void setDestinationUrl(@Pattern(regexp = "^https?://.*", message = "La URL debe comenzar con http:// o https://") String destinationUrl) {
        this.destinationUrl = destinationUrl;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public Boolean getForce() {
        return force;
    }

    public void setForce(Boolean force) {
        this.force = force;
    }

    public @Min(value = 1, message = "La prioridad debe ser al menos 1") @Max(value = 10, message = "La prioridad no puede ser mayor a 10") Integer getPriority() {
        return priority;
    }

    public void setPriority(@Min(value = 1, message = "La prioridad debe ser al menos 1") @Max(value = 10, message = "La prioridad no puede ser mayor a 10") Integer priority) {
        this.priority = priority;
    }

    public @Min(value = 1000, message = "El timeout debe ser al menos 1000 ms") @Max(value = 60000, message = "El timeout no puede exceder 60000 ms (1 minuto)") Integer getTimeout() {
        return timeout;
    }

    public void setTimeout(@Min(value = 1000, message = "El timeout debe ser al menos 1000 ms") @Max(value = 60000, message = "El timeout no puede exceder 60000 ms (1 minuto)") Integer timeout) {
        this.timeout = timeout;
    }

    public Object getHeaders() {
        return headers;
    }

    public void setHeaders(Object headers) {
        this.headers = headers;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public Boolean getDirectMode() {
        return directMode;
    }

    public void setDirectMode(Boolean directMode) {
        this.directMode = directMode;
    }

    public int getHours() {
        return 0;
    }
}