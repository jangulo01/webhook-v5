package com.sg.webhookservice.presentation.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * DTO genérico para respuestas de operaciones.
 * Proporciona un formato estándar para todas las respuestas exitosas.
 *
 * @param <T> Tipo de datos del resultado
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    /**
     * Estado de la operación.
     */
    private String status;

    /**
     * Mensaje descriptivo del resultado.
     */
    private String message;

    /**
     * Datos del resultado (opcional).
     */
    private T data;

    /**
     * Código de resultado (opcional).
     */
    private String code;

    /**
     * Tiempo en que se completó la operación.
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
    private OffsetDateTime timestamp;

    /**
     * Crea una respuesta exitosa.
     *
     * @param message Mensaje descriptivo
     * @param data Datos del resultado
     * @param <T> Tipo de datos
     * @return Respuesta exitosa
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .status("success")
                .message(message)
                .data(data)
                .timestamp(OffsetDateTime.now())
                .build();
    }

    /**
     * Crea una respuesta exitosa sin datos.
     *
     * @param message Mensaje descriptivo
     * @param <T> Tipo de datos
     * @return Respuesta exitosa sin datos
     */
    public static <T> ApiResponse<T> success(String message) {
        return success(message, null);
    }

    /**
     * Crea una respuesta de error.
     *
     * @param message Mensaje descriptivo
     * @param code Código de error (opcional)
     * @param <T> Tipo de datos
     * @return Respuesta de error
     */
    public static <T> ApiResponse<T> error(String message, String code) {
        return ApiResponse.<T>builder()
                .status("error")
                .message(message)
                .code(code)
                .timestamp(OffsetDateTime.now())
                .build();
    }

    /**
     * Crea una respuesta de error sin código.
     *
     * @param message Mensaje descriptivo
     * @param <T> Tipo de datos
     * @return Respuesta de error sin código
     */
    public static <T> ApiResponse<T> error(String message) {
        return error(message, null);
    }
}