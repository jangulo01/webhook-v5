package com.sg.webhookservice.presentation.exception;

/**
 * Excepción lanzada cuando ocurre un error durante el procesamiento de un webhook.
 * Esta excepción puede representar diferentes fases del procesamiento
 * y contiene información detallada sobre el error.
 */
public class WebhookProcessingException extends RuntimeException {

    /**
     * Fase del procesamiento en la que ocurrió el error.
     */
    private final ProcessingPhase processingPhase;

    /**
     * Nombre del webhook relacionado con el error.
     */
    private final String webhookName;

    /**
     * ID del mensaje relacionado con el error (puede ser null).
     */
    private final String messageId;

    /**
     * Detalles técnicos adicionales del error (para logging).
     */
    private final String technicalDetails;

    /**
     * Constructor con mensaje de error.
     *
     * @param message Mensaje detallado del error
     */
    public WebhookProcessingException(String message) {
        super(message);
        this.processingPhase = ProcessingPhase.UNKNOWN;
        this.webhookName = null;
        this.messageId = null;
        this.technicalDetails = null;
    }

    /**
     * Constructor con mensaje de error y fase de procesamiento.
     *
     * @param message Mensaje detallado del error
     * @param processingPhase Fase de procesamiento donde ocurrió el error
     */
    public WebhookProcessingException(String message, ProcessingPhase processingPhase) {
        super(message);
        this.processingPhase = processingPhase;
        this.webhookName = null;
        this.messageId = null;
        this.technicalDetails = null;
    }

    /**
     * Constructor completo.
     *
     * @param message Mensaje detallado del error
     * @param processingPhase Fase de procesamiento donde ocurrió el error
     * @param webhookName Nombre del webhook relacionado
     * @param messageId ID del mensaje relacionado (puede ser null)
     */
    public WebhookProcessingException(String message, ProcessingPhase processingPhase,
                                      String webhookName, String messageId) {
        super(message);
        this.processingPhase = processingPhase;
        this.webhookName = webhookName;
        this.messageId = messageId;
        this.technicalDetails = null;
    }

    /**
     * Constructor completo con detalles técnicos.
     *
     * @param message Mensaje detallado del error
     * @param processingPhase Fase de procesamiento donde ocurrió el error
     * @param webhookName Nombre del webhook relacionado
     * @param messageId ID del mensaje relacionado (puede ser null)
     * @param technicalDetails Detalles técnicos adicionales
     */
    public WebhookProcessingException(String message, ProcessingPhase processingPhase,
                                      String webhookName, String messageId,
                                      String technicalDetails) {
        super(message);
        this.processingPhase = processingPhase;
        this.webhookName = webhookName;
        this.messageId = messageId;
        this.technicalDetails = technicalDetails;
    }

    /**
     * Constructor con causa de la excepción.
     *
     * @param message Mensaje detallado del error
     * @param cause Excepción original que causó este error
     */
    public WebhookProcessingException(String message, Throwable cause) {
        super(message, cause);
        this.processingPhase = ProcessingPhase.UNKNOWN;
        this.webhookName = null;
        this.messageId = null;
        this.technicalDetails = null;
    }

    /**
     * Constructor completo con causa.
     *
     * @param message Mensaje detallado del error
     * @param cause Excepción original que causó este error
     * @param processingPhase Fase de procesamiento donde ocurrió el error
     * @param webhookName Nombre del webhook relacionado
     * @param messageId ID del mensaje relacionado (puede ser null)
     */
    public WebhookProcessingException(String message, Throwable cause,
                                      ProcessingPhase processingPhase,
                                      String webhookName, String messageId) {
        super(message, cause);
        this.processingPhase = processingPhase;
        this.webhookName = webhookName;
        this.messageId = messageId;
        this.technicalDetails = null;
    }

    /**
     * Obtiene la fase de procesamiento donde ocurrió el error.
     *
     * @return Fase de procesamiento
     */
    public ProcessingPhase getProcessingPhase() {
        return processingPhase;
    }

    /**
     * Obtiene el nombre del webhook relacionado.
     *
     * @return Nombre del webhook o null si no se especificó
     */
    public String getWebhookName() {
        return webhookName;
    }

    /**
     * Obtiene el ID del mensaje relacionado.
     *
     * @return ID del mensaje o null si no se especificó
     */
    public String getMessageId() {
        return messageId;
    }

    /**
     * Obtiene los detalles técnicos adicionales.
     *
     * @return Detalles técnicos o null si no se especificaron
     */
    public String getTechnicalDetails() {
        return technicalDetails;
    }

    /**
     * Crea una excepción para un error de validación.
     *
     * @param message Mensaje detallado del error
     * @param webhookName Nombre del webhook relacionado
     * @return Nueva excepción WebhookProcessingException
     */
    public static WebhookProcessingException validationError(String message, String webhookName) {
        return new WebhookProcessingException(
                message,
                ProcessingPhase.VALIDATION,
                webhookName,
                null
        );
    }

    /**
     * Crea una excepción para un error de entrega.
     *
     * @param message Mensaje detallado del error
     * @param webhookName Nombre del webhook relacionado
     * @param messageId ID del mensaje relacionado
     * @return Nueva excepción WebhookProcessingException
     */
    public static WebhookProcessingException deliveryError(String message, String webhookName, String messageId) {
        return new WebhookProcessingException(
                message,
                ProcessingPhase.DELIVERY,
                webhookName,
                messageId
        );
    }

    /**
     * Crea una excepción para un error de serialización.
     *
     * @param message Mensaje detallado del error
     * @param cause Excepción original que causó este error
     * @return Nueva excepción WebhookProcessingException
     */
    public static WebhookProcessingException serializationError(String message, Throwable cause) {
        return new WebhookProcessingException(
                message,
                cause,
                ProcessingPhase.SERIALIZATION,
                null,
                null
        );
    }

    /**
     * Fases del procesamiento de webhooks donde pueden ocurrir errores.
     */
    public enum ProcessingPhase {
        /**
         * Fase de validación del webhook y sus datos.
         */
        VALIDATION,

        /**
         * Fase de serialización de datos.
         */
        SERIALIZATION,

        /**
         * Fase de generación de firma HMAC.
         */
        SIGNATURE,

        /**
         * Fase de guardado en base de datos.
         */
        PERSISTENCE,

        /**
         * Fase de publicación en Kafka.
         */
        KAFKA_PUBLISHING,

        /**
         * Fase de consumo desde Kafka.
         */
        KAFKA_CONSUMPTION,

        /**
         * Fase de preparación para entrega.
         */
        PREPARATION,

        /**
         * Fase de entrega al destino.
         */
        DELIVERY,

        /**
         * Fase de programación de reintentos.
         */
        RETRY_SCHEDULING,

        /**
         * Fase desconocida.
         */
        UNKNOWN,

        RETRY
    }
}