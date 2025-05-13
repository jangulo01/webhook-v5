package com.sg.webhookservice.exception;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Modelo estandarizado para respuestas de error en la API.
 * 
 * Proporciona una estructura consistente para todos los errores
 * devueltos por la API, incluyendo:
 * - Código de error específico
 * - Mensaje descriptivo
 * - Código de estado HTTP
 * - Timestamp del error
 * - Ruta de la solicitud que generó el error
 * - Detalles de validación (cuando aplica)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Respuesta estandarizada para errores de API")
public class ErrorResponse {
    
    /**
     * Código de error específico de la aplicación.
     */
    @Schema(description = "Código de error específico de la aplicación", 
            example = "RESOURCE_NOT_FOUND")
    private String errorCode;
    
    /**
     * Mensaje descriptivo del error.
     */
    @Schema(description = "Mensaje descriptivo del error", 
            example = "El recurso solicitado no fue encontrado")
    private String message;
    
    /**
     * Código de estado HTTP.
     */
    @Schema(description = "Código de estado HTTP", example = "404")
    private int status;
    
    /**
     * Marca temporal cuando ocurrió el error.
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
    @Schema(description = "Marca temporal cuando ocurrió el error", 
            example = "2023-01-01T12:00:00.000Z")
    private LocalDateTime timestamp;
    
    /**
     * Ruta de la solicitud que generó el error.
     */
    @Schema(description = "Ruta de la solicitud que generó el error", 
            example = "/webhooks/non-existent")
    private String path;
    
    /**
     * Información técnica adicional (opcional).
     * Solo se incluye en entornos de desarrollo o para usuarios administradores.
     */
    @Schema(description = "Información técnica adicional (solo para desarrollo/admin)", 
            example = "NullPointerException en WebhookService.java:156")
    private String debugInfo;
    
    /**
     * Identificador de rastreo para correlacionar con logs.
     */
    @Schema(description = "Identificador único para rastrear este error en los logs", 
            example = "e4567-e89b-12d3-a456")
    private String traceId;
    
    /**
     * Detalles de errores de validación.
     * Solo se incluye cuando el error está relacionado con validación.
     */
    @Schema(description = "Detalles de errores de validación")
    @Builder.Default
    private List<ValidationError> errors = new ArrayList<>();
    
    /**
     * Enlace a documentación de ayuda relacionada con este error.
     */
    @Schema(description = "URL a documentación relacionada con este error", 
            example = "https://docs.example.com/errors/resource-not-found")
    private String helpUrl;
    
    /**
     * Clase interna para representar errores de validación.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Error de validación específico")
    public static class ValidationError {
        /**
         * Campo que falló la validación.
         */
        @Schema(description = "Campo que falló la validación", example = "email")
        private String field;
        
        /**
         * Mensaje de error específico para el campo.
         */
        @Schema(description = "Mensaje de error para el campo", 
                example = "Debe ser una dirección de correo válida")
        private String message;
        
        /**
         * Valor rechazado (opcional, puede omitirse para valores sensibles).
         */
        @Schema(description = "Valor que falló la validación", 
                example = "invalid-email")
        private String rejectedValue;
        
        /**
         * Código de error específico para esta validación.
         */
        @Schema(description = "Código de error específico para esta validación", 
                example = "INVALID_FORMAT")
        private String code;
    }
    
    /**
     * Método de fábrica para crear una respuesta de error básica.
     * 
     * @param errorCode Código de error
     * @param message Mensaje de error
     * @param status Código de estado HTTP
     * @param path Ruta de la solicitud
     * @return Instancia de ErrorResponse
     */
    public static ErrorResponse of(String errorCode, String message, int status, String path) {
        return ErrorResponse.builder()
                .errorCode(errorCode)
                .message(message)
                .status(status)
                .timestamp(LocalDateTime.now())
                .path(path)
                .build();
    }
    
    /**
     * Método de fábrica para crear una respuesta de error a partir de una ApiException.
     * 
     * @param ex Excepción de la API
     * @param path Ruta de la solicitud
     * @return Instancia de ErrorResponse
     */
    public static ErrorResponse fromException(ApiException ex, String path) {
        return ErrorResponse.builder()
                .errorCode(ex.getErrorCode())
                .message(ex.getMessage())
                .status(ex.getStatus().value())
                .timestamp(LocalDateTime.now())
                .path(path)
                .build();
    }
    
    /**
     * Método de fábrica para crear una respuesta de error para validación fallida.
     * 
     * @param message Mensaje general de error
     * @param path Ruta de la solicitud
     * @param errors Lista de errores de validación
     * @return Instancia de ErrorResponse
     */
    public static ErrorResponse validationError(String message, String path, List<ValidationError> errors) {
        return ErrorResponse.builder()
                .errorCode("VALIDATION_ERROR")
                .message(message)
                .status(400)
                .timestamp(LocalDateTime.now())
                .path(path)
                .errors(errors)
                .build();
    }
    
    /**
     * Agrega un error de validación a la lista de errores.
     * 
     * @param field Campo que falló la validación
     * @param message Mensaje de error
     * @param rejectedValue Valor rechazado
     * @return Esta instancia de ErrorResponse para encadenamiento
     */
    public ErrorResponse addValidationError(String field, String message, String rejectedValue) {
        if (this.errors == null) {
            this.errors = new ArrayList<>();
        }
        this.errors.add(new ValidationError(field, message, rejectedValue, null));
        return this;
    }
    
    /**
     * Agrega un error de validación a la lista de errores.
     * 
     * @param field Campo que falló la validación
     * @param message Mensaje de error
     * @param rejectedValue Valor rechazado
     * @param code Código específico del error
     * @return Esta instancia de ErrorResponse para encadenamiento
     */
    public ErrorResponse addValidationError(String field, String message, String rejectedValue, String code) {
        if (this.errors == null) {
            this.errors = new ArrayList<>();
        }
        this.errors.add(new ValidationError(field, message, rejectedValue, code));
        return this;
    }
}