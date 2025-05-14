package com.sg.webhookservice.presentation.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * DTO para la configuración de webhooks.
 * Se utiliza para crear, actualizar y devolver información de configuraciones de webhook.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Configuración de un webhook")
public class WebhookConfigDto {

    @Schema(description = "ID único de la configuración", example = "123e4567-e89b-12d3-a456-426614174000", accessMode = Schema.AccessMode.READ_ONLY)
    private UUID id;

    @NotBlank(message = "El nombre del webhook es obligatorio")
    @Pattern(regexp = "^[a-zA-Z0-9-_]{3,50}$", message = "El nombre debe contener solo letras, números, guiones y guiones bajos, con longitud entre 3 y 50 caracteres")
    @Schema(description = "Nombre único del webhook", example = "payment-notification", required = true)
    private String name;

    @NotBlank(message = "La URL de destino es obligatoria")
    @Pattern(regexp = "^https?://.*", message = "La URL debe comenzar con http:// o https://")
    @Schema(description = "URL a la que se enviará el webhook", example = "https://example.com/webhook-receiver", required = true)
    private String targetUrl;

    @NotBlank(message = "El secreto es obligatorio")
    @Size(min = 8, max = 100, message = "El secreto debe tener entre 8 y 100 caracteres")
    @Schema(description = "Secreto usado para firmar los webhooks", example = "your-secret-key", required = true)
    private String secret;

    @Min(value = 0, message = "El máximo de reintentos debe ser al menos 0")
    @Max(value = 10, message = "El máximo de reintentos no puede superar 10")
    @Schema(description = "Número máximo de reintentos en caso de fallo", example = "3", defaultValue = "3")
    private Integer maxRetries;

    @Schema(description = "Estrategia de backoff para reintentos", example = "exponential",
            allowableValues = {"linear", "exponential", "fixed"}, defaultValue = "exponential")
    private String backoffStrategy;

    @Min(value = 5, message = "El intervalo inicial debe ser al menos 5 segundos")
    @Max(value = 3600, message = "El intervalo inicial no puede superar 1 hora (3600 segundos)")
    @Schema(description = "Intervalo inicial entre reintentos (segundos)", example = "60", defaultValue = "60")
    private Integer initialInterval;

    @DecimalMin(value = "1.0", message = "El factor de backoff debe ser al menos 1.0")
    @DecimalMax(value = "5.0", message = "El factor de backoff no puede superar 5.0")
    @Schema(description = "Factor de multiplicación para backoff exponencial", example = "2.0", defaultValue = "2.0")
    private Double backoffFactor;

    @Min(value = 60, message = "El intervalo máximo debe ser al menos 1 minuto (60 segundos)")
    @Max(value = 86400, message = "El intervalo máximo no puede superar 1 día (86400 segundos)")
    @Schema(description = "Intervalo máximo entre reintentos (segundos)", example = "3600", defaultValue = "3600")
    private Integer maxInterval;

    @Min(value = 3600, message = "La edad máxima debe ser al menos 1 hora (3600 segundos)")
    @Max(value = 2592000, message = "La edad máxima no puede superar 30 días (2592000 segundos)")
    @Schema(description = "Tiempo máximo de vida para mensajes (segundos)", example = "86400", defaultValue = "86400")
    private Integer maxAge;

    @Schema(description = "Cabeceras HTTP personalizadas a incluir en los webhooks", example = "{\"X-Custom-Header\": \"value\"}")
    private Map<String, String> headers;

    @Schema(description = "Estado de activación del webhook", example = "true", defaultValue = "true")
    private Boolean active;

    @Schema(description = "Fecha de creación", accessMode = Schema.AccessMode.READ_ONLY)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
    private OffsetDateTime createdAt;

    @Schema(description = "Fecha de última actualización", accessMode = Schema.AccessMode.READ_ONLY)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
    private OffsetDateTime updatedAt;

    /**
     * Establece valores predeterminados para campos opcionales
     */
    public void setDefaultsIfNull() {
        if (maxRetries == null) maxRetries = 3;
        if (backoffStrategy == null) backoffStrategy = "exponential";
        if (initialInterval == null) initialInterval = 60;
        if (backoffFactor == null) backoffFactor = 2.0;
        if (maxInterval == null) maxInterval = 3600;
        if (maxAge == null) maxAge = 86400;
        if (active == null) active = true;
    }
}