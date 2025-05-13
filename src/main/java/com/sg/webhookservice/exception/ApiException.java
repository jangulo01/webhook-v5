package com.sg.webhookservice.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Clase base para todas las excepciones de la API.
 * 
 * Proporciona un mecanismo consistente para manejar errores en la API,
 * incluyendo:
 * - Códigos de estado HTTP apropiados
 * - Códigos de error específicos para la aplicación
 * - Mensajes descriptivos
 * - Capacidad para encapsular excepciones subyacentes
 */
@Getter
public abstract class ApiException extends RuntimeException {
    
    /**
     * Código de estado HTTP asociado con esta excepción.
     */
    private final HttpStatus status;
    
    /**
     * Código de error específico de la aplicación para categorizar el error.
     */
    private final String errorCode;
    
    /**
     * Información adicional relevante para el error.
     */
    private final String additionalInfo;
    
    /**
     * Constructor básico para excepciones de API.
     * 
     * @param message Mensaje descriptivo del error
     * @param status Código de estado HTTP a devolver
     * @param errorCode Código de error específico de la aplicación
     */
    public ApiException(String message, HttpStatus status, String errorCode) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
        this.additionalInfo = null;
    }
    
    /**
     * Constructor para excepciones de API con información adicional.
     * 
     * @param message Mensaje descriptivo del error
     * @param status Código de estado HTTP a devolver
     * @param errorCode Código de error específico de la aplicación
     * @param additionalInfo Información adicional relevante para el error
     */
    public ApiException(String message, HttpStatus status, String errorCode, String additionalInfo) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
        this.additionalInfo = additionalInfo;
    }
    
    /**
     * Constructor para excepciones de API con causa subyacente.
     * 
     * @param message Mensaje descriptivo del error
     * @param cause Excepción que causó este error
     * @param status Código de estado HTTP a devolver
     * @param errorCode Código de error específico de la aplicación
     */
    public ApiException(String message, Throwable cause, HttpStatus status, String errorCode) {
        super(message, cause);
        this.status = status;
        this.errorCode = errorCode;
        this.additionalInfo = null;
    }
    
    /**
     * Constructor para excepciones de API con causa subyacente e información adicional.
     * 
     * @param message Mensaje descriptivo del error
     * @param cause Excepción que causó este error
     * @param status Código de estado HTTP a devolver
     * @param errorCode Código de error específico de la aplicación
     * @param additionalInfo Información adicional relevante para el error
     */
    public ApiException(String message, Throwable cause, HttpStatus status, String errorCode, String additionalInfo) {
        super(message, cause);
        this.status = status;
        this.errorCode = errorCode;
        this.additionalInfo = additionalInfo;
    }
    
    /**
     * Determina si esta excepción representa un error del lado del cliente (4xx)
     * o del servidor (5xx).
     * 
     * @return true si es un error del cliente, false si es un error del servidor
     */
    public boolean isClientError() {
        return status.is4xxClientError();
    }
    
    /**
     * Genera un mensaje detallado que incluye tanto el mensaje principal
     * como la información adicional si está disponible.
     * 
     * @return Mensaje detallado del error
     */
    public String getDetailedMessage() {
        if (additionalInfo != null && !additionalInfo.isEmpty()) {
            return getMessage() + " - " + additionalInfo;
        }
        return getMessage();
    }
    
    /**
     * Devuelve el nombre simple de la clase de excepción como un
     * identificador adicional para el tipo de error.
     * 
     * @return Nombre simple de la clase de excepción
     */
    public String getExceptionName() {
        return this.getClass().getSimpleName();
    }
}