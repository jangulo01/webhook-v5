package com.sg.webhookservice.exception;

import lombok.Getter;

/**
 * Excepción para errores durante el procesamiento de webhooks.
 */
@Getter
public class WebhookProcessingException extends RuntimeException {

    /**
     * Fase del procesamiento donde ocurrió el error
     */
    public enum ProcessingPhase {
        PREPARATION,       // Fase de preparación inicial
        DELIVERY,          // Fase de envío al destino
        RESPONSE_HANDLING, // Fase de manejo de respuesta
        RETRY_SCHEDULING,  // Fase de programación de reintentos
        CLEANUP            // Fase de limpieza
    }

    /**
     * Fase del procesamiento donde ocurrió el error
     */
    private final ProcessingPhase phase;

    /**
     * Nombre del webhook relacionado con el error
     */
    private final String webhookName;

    /**
     * ID del mensaje relacionado con el error
     */
    private final String messageId;

    /**
     * Constructor para errores sin causa subyacente
     *
     * @param message Mensaje de error
     * @param phase Fase de procesamiento donde ocurrió el error
     * @param webhookName Nombre del webhook relacionado
     * @param messageId ID del mensaje relacionado
     */
    public WebhookProcessingException(String message, ProcessingPhase phase,
                                      String webhookName, String messageId) {
        super(message);
        this.phase = phase;
        this.webhookName = webhookName;
        this.messageId = messageId;
    }

    /**
     * Constructor para errores con causa subyacente
     *
     * @param message Mensaje de error
     * @param cause Excepción causante
     * @param phase Fase de procesamiento donde ocurrió el error
     * @param webhookName Nombre del webhook relacionado
     * @param messageId ID del mensaje relacionado
     */
    public WebhookProcessingException(String message, Throwable cause,
                                      ProcessingPhase phase, String webhookName, String messageId) {
        super(message, cause);
        this.phase = phase;
        this.webhookName = webhookName;
        this.messageId = messageId;
    }

    /**
     * Crea una excepción para errores en la fase de preparación
     *
     * @param message Mensaje de error
     * @param webhookName Nombre del webhook
     * @param messageId ID del mensaje
     * @return Nueva instancia de WebhookProcessingException
     */
    public static WebhookProcessingException preparationError(
            String message, String webhookName, String messageId) {
        return new WebhookProcessingException(message, ProcessingPhase.PREPARATION, webhookName, messageId);
    }

    /**
     * Crea una excepción para errores en la fase de entrega
     *
     * @param message Mensaje de error
     * @param cause Excepción causante
     * @param webhookName Nombre del webhook
     * @param messageId ID del mensaje
     * @return Nueva instancia de WebhookProcessingException
     */
    public static WebhookProcessingException deliveryError(
            String message, Throwable cause, String webhookName, String messageId) {
        return new WebhookProcessingException(message, cause, ProcessingPhase.DELIVERY, webhookName, messageId);
    }

    /**
     * Crea una excepción para errores en la fase de manejo de respuesta
     *
     * @param message Mensaje de error
     * @param webhookName Nombre del webhook
     * @param messageId ID del mensaje
     * @param statusCode Código de estado HTTP recibido
     * @return Nueva instancia de WebhookProcessingException
     */
    public static WebhookProcessingException responseHandlingError(
            String message, String webhookName, String messageId, int statusCode) {
        return new WebhookProcessingException(
                message + " (Status: " + statusCode + ")",
                ProcessingPhase.RESPONSE_HANDLING, webhookName, messageId);
    }

    /**
     * Crea una excepción para errores en la fase de programación de reintentos
     *
     * @param message Mensaje de error
     * @param cause Excepción causante
     * @param webhookName Nombre del webhook
     * @param messageId ID del mensaje
     * @return Nueva instancia de WebhookProcessingException
     */
    public static WebhookProcessingException retrySchedulingError(
            String message, Throwable cause, String webhookName, String messageId) {
        return new WebhookProcessingException(
                message, cause, ProcessingPhase.RETRY_SCHEDULING, webhookName, messageId);
    }

    @Override
    public String toString() {
        return String.format("WebhookProcessingException{message='%s', phase=%s, webhookName='%s', messageId='%s'}",
                getMessage(), phase, webhookName, messageId);
    }
}