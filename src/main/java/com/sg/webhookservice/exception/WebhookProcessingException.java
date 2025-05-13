package com.sg.webhookservice.exception;

import org.springframework.http.HttpStatus;

/**
 * Excepción lanzada cuando ocurre un error durante el procesamiento, 
 * entrega o reintento de webhooks.
 * 
 * Esta excepción se utiliza en casos como:
 * - Errores al enviar webhooks a destinos
 * - Problemas al procesar la respuesta del destinatario
 * - Fallos en la estrategia de reintentos
 * - Errores de formato o validación específicos de webhooks
 * - Problemas de timeout o conexión con destinos
 */
public class WebhookProcessingException extends ApiException {
    
    /**
     * Código de error estándar para esta excepción
     */
    private static final String ERROR_CODE = "WEBHOOK_PROCESSING_ERROR";
    
    /**
     * Estado HTTP predeterminado para esta excepción
     */
    private static final HttpStatus DEFAULT_STATUS = HttpStatus.INTERNAL_SERVER_ERROR;
    
    /**
     * Fase del procesamiento donde ocurrió el error
     */
    private final ProcessingPhase processingPhase;
    
    /**
     * Nombre del webhook relacionado con el error
     */
    private final String webhookName;
    
    /**
     * ID del mensaje relacionado con el error
     */
    private final String messageId;
    
    /**
     * Constructor básico con mensaje de error.
     * 
     * @param message Mensaje descriptivo del error
     */
    public WebhookProcessingException(String message) {
        super(message, DEFAULT_STATUS, ERROR_CODE);
        this.processingPhase = ProcessingPhase.UNKNOWN;
        this.webhookName = null;
        this.messageId = null;
    }
    
    /**
     * Constructor con fase de procesamiento.
     * 
     * @param message Mensaje descriptivo del error
     * @param processingPhase Fase donde ocurrió el error
     */
    public WebhookProcessingException(String message, ProcessingPhase processingPhase) {
        super(message, DEFAULT_STATUS, ERROR_CODE);
        this.processingPhase = processingPhase;
        this.webhookName = null;
        this.messageId = null;
    }
    
    /**
     * Constructor con fase, nombre de webhook e ID de mensaje.
     * 
     * @param message Mensaje descriptivo del error
     * @param processingPhase Fase donde ocurrió el error
     * @param webhookName Nombre del webhook
     * @param messageId ID del mensaje
     */
    public WebhookProcessingException(String message, ProcessingPhase processingPhase, 
                                    String webhookName, String messageId) {
        super(message, DEFAULT_STATUS, ERROR_CODE);
        this.processingPhase = processingPhase;
        this.webhookName = webhookName;
        this.messageId = messageId;
    }
    
    /**
     * Constructor para encapsular otra excepción.
     * 
     * @param message Mensaje descriptivo del error
     * @param cause Excepción original
     * @param processingPhase Fase donde ocurrió el error
     */
    public WebhookProcessingException(String message, Throwable cause, ProcessingPhase processingPhase) {
        super(message, cause, DEFAULT_STATUS, ERROR_CODE);
        this.processingPhase = processingPhase;
        this.webhookName = null;
        this.messageId = null;
    }
    
    /**
     * Constructor completo con todos los parámetros.
     * 
     * @param message Mensaje descriptivo del error
     * @param cause Excepción original
     * @param processingPhase Fase donde ocurrió el error
     * @param webhookName Nombre del webhook
     * @param messageId ID del mensaje
     * @param additionalInfo Información adicional sobre el error
     */
    public WebhookProcessingException(String message, Throwable cause, 
                                    ProcessingPhase processingPhase, 
                                    String webhookName, String messageId, 
                                    String additionalInfo) {
        super(message, cause, DEFAULT_STATUS, ERROR_CODE, additionalInfo);
        this.processingPhase = processingPhase;
        this.webhookName = webhookName;
        this.messageId = messageId;
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
     * Obtiene el nombre del webhook relacionado con el error.
     * 
     * @return Nombre del webhook o null si no está disponible
     */
    public String getWebhookName() {
        return webhookName;
    }
    
    /**
     * Obtiene el ID del mensaje relacionado con el error.
     * 
     * @return ID del mensaje o null si no está disponible
     */
    public String getMessageId() {
        return messageId;
    }
    
    /**
     * Genera una instancia para error de conexión con destino.
     * 
     * @param webhookName Nombre del webhook
     * @param messageId ID del mensaje
     * @param targetUrl URL de destino
     * @param cause Excepción original
     * @return Instancia de WebhookProcessingException
     */
    public static WebhookProcessingException connectionError(
            String webhookName, String messageId, String targetUrl, Throwable cause) {
        return new WebhookProcessingException(
            "Error de conexión con destino de webhook",
            cause,
            ProcessingPhase.DELIVERY,
            webhookName,
            messageId,
            "URL destino: " + targetUrl
        );
    }
    
    /**
     * Genera una instancia para timeout en la entrega.
     * 
     * @param webhookName Nombre del webhook
     * @param messageId ID del mensaje
     * @param targetUrl URL de destino
     * @param timeoutMs Timeout en milisegundos
     * @return Instancia de WebhookProcessingException
     */
    public static WebhookProcessingException deliveryTimeout(
            String webhookName, String messageId, String targetUrl, int timeoutMs) {
        return new WebhookProcessingException(
            "Timeout al entregar webhook",
            ProcessingPhase.DELIVERY,
            webhookName,
            messageId
        );
    }
    
    /**
     * Genera una instancia para respuesta de error del servidor destino.
     * 
     * @param webhookName Nombre del webhook
     * @param messageId ID del mensaje
     * @param statusCode Código de estado HTTP
     * @param responseBody Cuerpo de la respuesta
     * @return Instancia de WebhookProcessingException
     */
    public static WebhookProcessingException targetServerError(
            String webhookName, String messageId, int statusCode, String responseBody) {
        return new WebhookProcessingException(
            String.format("El servidor destino respondió con error: HTTP %d", statusCode),
            ProcessingPhase.DELIVERY,
            webhookName,
            messageId
        );
    }
    
    /**
     * Genera una instancia para error en la preparación del mensaje.
     * 
     * @param webhookName Nombre del webhook
     * @param cause Excepción original
     * @return Instancia de WebhookProcessingException
     */
    public static WebhookProcessingException messagePreparationError(
            String webhookName, Throwable cause) {
        return new WebhookProcessingException(
            "Error al preparar mensaje de webhook",
            cause,
            ProcessingPhase.PREPARATION
        );
    }
    
    /**
     * Genera una instancia para error al calcular reintentos.
     * 
     * @param webhookName Nombre del webhook
     * @param messageId ID del mensaje
     * @param retryCount Número de reintentos actuales
     * @param cause Excepción original
     * @return Instancia de WebhookProcessingException
     */
    public static WebhookProcessingException retryCalculationError(
            String webhookName, String messageId, int retryCount, Throwable cause) {
        return new WebhookProcessingException(
            "Error al calcular estrategia de reintentos",
            cause,
            ProcessingPhase.RETRY_SCHEDULING,
            webhookName,
            messageId,
            "Intento actual: " + retryCount
        );
    }
    
    /**
     * Genera una instancia para reintentos agotados.
     * 
     * @param webhookName Nombre del webhook
     * @param messageId ID del mensaje
     * @param maxRetries Número máximo de reintentos
     * @return Instancia de WebhookProcessingException
     */
    public static WebhookProcessingException retriesExhausted(
            String webhookName, String messageId, int maxRetries) {
        return new WebhookProcessingException(
            String.format("Reintentos agotados (%d) para webhook", maxRetries),
            ProcessingPhase.RETRY_SCHEDULING,
            webhookName,
            messageId
        );
    }
    
    /**
     * Genera una instancia para error en la cancelación de un mensaje.
     * 
     * @param messageId ID del mensaje
     * @param status Estado actual del mensaje
     * @return Instancia de WebhookProcessingException
     */
    public static WebhookProcessingException cannotCancelMessage(String messageId, String status) {
        return new WebhookProcessingException(
            String.format("No se puede cancelar el mensaje en estado '%s'", status),
            ProcessingPhase.CANCELLATION,
            null,
            messageId
        );
    }
    
    /**
     * Fases del procesamiento de webhooks.
     */
    public enum ProcessingPhase {
        /**
         * Fase de validación inicial del webhook
         */
        VALIDATION,
        
        /**
         * Fase de preparación del mensaje antes de envío
         */
        PREPARATION,
        
        /**
         * Fase de envío al destino
         */
        DELIVERY,
        
        /**
         * Fase de procesamiento de la respuesta
         */
        RESPONSE_PROCESSING,
        
        /**
         * Fase de programación de reintentos
         */
        RETRY_SCHEDULING,
        
        /**
         * Fase de cancelación
         */
        CANCELLATION,
        
        /**
         * Fase de limpieza o finalización
         */
        CLEANUP,
        
        /**
         * Fase desconocida
         */
        UNKNOWN
    }
}