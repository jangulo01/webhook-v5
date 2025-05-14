package com.sg.webhookservice.presentation.exception;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * Clase base para respuestas de error.
 * Define el formato estándar de errores en la API.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    /**
     * Código de estado HTTP.
     */
    private int status;

    /**
     * Tipo de error.
     */
    private String error;

    /**
     * Mensaje descriptivo del error.
     */
    private String message;

    /**
     * Tiempo en que ocurrió el error.
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
    private OffsetDateTime timestamp;

    /**
     * URL de la documentación con más información (opcional).
     */
    private String moreInfo;

    /**
     * Constructor que no incluye la URL de más información.
     */
    public ErrorResponse(int status, String error, String message, OffsetDateTime timestamp) {
        this.status = status;
        this.error = error;
        this.message = message;
        this.timestamp = timestamp;
    }
}