package com.sg.webhookservice.presentation.exception;

import lombok.Getter;

import java.util.UUID;

/**
 * Excepción para errores durante el procesamiento de webhooks.
 */
@Getter
public class WebhookProcessingException1 extends RuntimeException {

    /**
     * Fase del procesamiento donde ocurrió el error.
     */
    private ProcessingPhase phase = null;

    /**
     * Nombre del webhook relacionado con el error.
     */
    private String webhookName = "";

    /**
     * ID del mensaje relacionado con el error (puede ser null).
     */
    private String messageId = "";

    /**
     * Constructor para error con mensaje y fase.
     *
     * @param message Mensaje descriptivo
     * @param phase Fase del procesamiento
     */
    public WebhookProcessingException1(String message, ProcessingPhase phase) {
        super(message);
        this.phase = phase;
        this.webhookName = null;
        this.messageId = null;
    }

    /**
     * Constructor para error con mensaje, fase y webhook.
     *
     * @param message Mensaje descriptivo
     * @param phase Fase del procesamiento
     * @param webhookName Nombre del webhook
     */
    public WebhookProcessingException1(String message, ProcessingPhase phase, String webhookName) {
        super(message);
        this.phase = phase;
        this.webhookName = webhookName;
        this.messageId = null;
    }

    /**
     * Constructor para error con mensaje, fase, webhook e ID de mensaje.
     *
     * @param message Mensaje descriptivo
     * @param phase Fase del procesamiento
     * @param webhookName Nombre del webhook
     * @param messageId ID del mensaje (como String)
     */
    public WebhookProcessingException1(String message, ProcessingPhase phase, String webhookName, String messageId) {
        super(message);
        this.phase = phase;
        this.webhookName = webhookName;
        this.messageId = messageId;
    }

    /**
     * Constructor para error con mensaje, fase, webhook e ID de mensaje como UUID.
     *
     * @param message Mensaje descriptivo
     * @param phase Fase del procesamiento
     * @param webhookName Nombre del webhook
     * @param messageId ID del mensaje (como UUID)
     */
    public WebhookProcessingException1(String message, ProcessingPhase phase, String webhookName, UUID messageId) {
        super(message);
        this.phase = phase;
        this.webhookName = webhookName;
        this.messageId = messageId != null ? messageId.toString() : null;
    }

    /**
     * Fases del procesamiento de webhooks.
     */
    public enum ProcessingPhase {
        /**
         * Error durante la validación
         */
        VALIDATION,

        /**
         * Error durante la serialización
         */
        SERIALIZATION,

        /**
         * Error durante la firma
         */
        SIGNING,

        /**
         * Error durante la entrega
         */
        DELIVERY,

        /**
         * Error durante el reintento
         */
        RETRY,

        /**
         * Error del sistema
         */
        SYSTEM
    }
}