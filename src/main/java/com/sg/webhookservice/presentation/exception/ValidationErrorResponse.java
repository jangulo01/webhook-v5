package com.sg.webhookservice.presentation.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Respuesta de error especializada para errores de validación.
 * Incluye detalles sobre los campos con error y sus mensajes.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ValidationErrorResponse extends ErrorResponse {

    /**
     * Mapa de errores de validación, con el nombre del campo y la lista de mensajes de error.
     */
    private Map<String, List<String>> fieldErrors;

    /**
     * Constructor completo.
     *
     * @param status Código de estado HTTP
     * @param error Tipo de error
     * @param message Mensaje descriptivo del error
     * @param timestamp Tiempo en que ocurrió el error
     * @param fieldErrors Mapa de errores de validación
     */
    public ValidationErrorResponse(int status, String error, String message, OffsetDateTime timestamp,
                                   Map<String, List<String>> fieldErrors) {
        super(status, error, message, timestamp);
        this.fieldErrors = fieldErrors;
    }

    /**
     * Constructor con URL de más información.
     *
     * @param status Código de estado HTTP
     * @param error Tipo de error
     * @param message Mensaje descriptivo del error
     * @param timestamp Tiempo en que ocurrió el error
     * @param moreInfo URL de la documentación con más información
     * @param fieldErrors Mapa de errores de validación
     */
    public ValidationErrorResponse(int status, String error, String message, OffsetDateTime timestamp,
                                   String moreInfo, Map<String, List<String>> fieldErrors) {
        super(status, error, message, timestamp, moreInfo);
        this.fieldErrors = fieldErrors;
    }

    /**
     * Obtiene el número total de errores de validación.
     *
     * @return Número total de errores
     */
    public int getTotalErrors() {
        if (fieldErrors == null) {
            return 0;
        }

        return fieldErrors.values().stream()
                .mapToInt(List::size)
                .sum();
    }

    /**
     * Verifica si hay errores en un campo específico.
     *
     * @param fieldName Nombre del campo a verificar
     * @return true si hay errores en ese campo
     */
    public boolean hasErrorForField(String fieldName) {
        if (fieldErrors == null) {
            return false;
        }

        return fieldErrors.containsKey(fieldName) &&
                !fieldErrors.get(fieldName).isEmpty();
    }

    /**
     * Obtiene el primer mensaje de error para un campo específico.
     *
     * @param fieldName Nombre del campo
     * @return Primer mensaje de error o null si no hay errores
     */
    public String getFirstErrorForField(String fieldName) {
        if (!hasErrorForField(fieldName)) {
            return null;
        }

        return fieldErrors.get(fieldName).get(0);
    }

    /**
     * Método de utilidad para construir rápidamente una respuesta de error de validación.
     *
     * @param field Nombre del campo con error
     * @param message Mensaje de error
     * @return Nueva instancia de ValidationErrorResponse
     */
    public static ValidationErrorResponse fromSingleError(String field, String message) {
        Map<String, List<String>> errors = Map.of(field, List.of(message));

        return new ValidationErrorResponse(
                400,
                "Validation error",
                "La solicitud contiene errores de validación",
                OffsetDateTime.now(),
                errors
        );
    }
}
