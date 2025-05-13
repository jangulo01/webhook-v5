package com.sg.webhookservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.kafka.KafkaException;

/**
 * Excepción lanzada cuando ocurre un error durante el procesamiento 
 * de mensajes con Kafka.
 * 
 * Esta excepción se utiliza en casos como:
 * - Errores de conexión con Kafka
 * - Fallos en la producción de mensajes
 * - Problemas en el consumo de mensajes
 * - Errores de serialización/deserialización
 * - Fallos en los callbacks de Kafka
 */
public class KafkaProcessingException extends ApiException {
    
    /**
     * Código de error estándar para esta excepción
     */
    private static final String ERROR_CODE = "KAFKA_PROCESSING_ERROR";
    
    /**
     * Estado HTTP predeterminado para esta excepción
     */
    private static final HttpStatus DEFAULT_STATUS = HttpStatus.INTERNAL_SERVER_ERROR;
    
    /**
     * Tipo de operación Kafka que causó el error
     */
    private final OperationType operationType;
    
    /**
     * Tópico de Kafka relacionado con el error
     */
    private final String topic;
    
    /**
     * Constructor básico con mensaje de error.
     * 
     * @param message Mensaje descriptivo del error
     * @param operationType Tipo de operación Kafka
     */
    public KafkaProcessingException(String message, OperationType operationType) {
        super(message, DEFAULT_STATUS, ERROR_CODE);
        this.operationType = operationType;
        this.topic = null;
    }
    
    /**
     * Constructor con mensaje de error y tópico.
     * 
     * @param message Mensaje descriptivo del error
     * @param operationType Tipo de operación Kafka
     * @param topic Tópico de Kafka relacionado
     */
    public KafkaProcessingException(String message, OperationType operationType, String topic) {
        super(message, DEFAULT_STATUS, ERROR_CODE);
        this.operationType = operationType;
        this.topic = topic;
    }
    
    /**
     * Constructor para encapsular excepciones de Kafka.
     * 
     * @param message Mensaje descriptivo del error
     * @param cause Excepción original de Kafka
     * @param operationType Tipo de operación Kafka
     * @param topic Tópico de Kafka relacionado
     */
    public KafkaProcessingException(String message, Throwable cause, 
                                  OperationType operationType, String topic) {
        super(message, cause, DEFAULT_STATUS, ERROR_CODE);
        this.operationType = operationType;
        this.topic = topic;
    }
    
    /**
     * Constructor completo con todos los parámetros.
     * 
     * @param message Mensaje descriptivo del error
     * @param cause Excepción que causó este error
     * @param operationType Tipo de operación Kafka
     * @param topic Tópico de Kafka relacionado
     * @param additionalInfo Información adicional sobre el error
     */
    public KafkaProcessingException(String message, Throwable cause, 
                                  OperationType operationType, String topic, 
                                  String additionalInfo) {
        super(message, cause, DEFAULT_STATUS, ERROR_CODE, additionalInfo);
        this.operationType = operationType;
        this.topic = topic;
    }
    
    /**
     * Obtiene el tipo de operación que causó el error.
     * 
     * @return Tipo de operación Kafka
     */
    public OperationType getOperationType() {
        return operationType;
    }
    
    /**
     * Obtiene el tópico relacionado con el error.
     * 
     * @return Tópico de Kafka o null si no es aplicable
     */
    public String getTopic() {
        return topic;
    }
    
    /**
     * Crea una instancia para errores de conexión.
     * 
     * @param bootstrapServers Servidores de bootstrap Kafka
     * @param cause Excepción original
     * @return Instancia de KafkaProcessingException
     */
    public static KafkaProcessingException connectionError(String bootstrapServers, Throwable cause) {
        return new KafkaProcessingException(
            "Error conectando a los servidores Kafka",
            cause,
            OperationType.CONNECTION,
            null,
            "Servidores: " + bootstrapServers
        );
    }
    
    /**
     * Crea una instancia para errores de producción de mensajes.
     * 
     * @param topic Tópico de Kafka
     * @param cause Excepción original
     * @return Instancia de KafkaProcessingException
     */
    public static KafkaProcessingException producerError(String topic, Throwable cause) {
        return new KafkaProcessingException(
            "Error al producir mensaje en Kafka",
            cause,
            OperationType.PRODUCE,
            topic
        );
    }
    
    /**
     * Crea una instancia para errores de producción de mensajes con mensaje ID.
     * 
     * @param topic Tópico de Kafka
     * @param messageId ID del mensaje que causó el error
     * @param cause Excepción original
     * @return Instancia de KafkaProcessingException
     */
    public static KafkaProcessingException producerError(String topic, String messageId, Throwable cause) {
        return new KafkaProcessingException(
            "Error al producir mensaje en Kafka",
            cause,
            OperationType.PRODUCE,
            topic,
            "Message ID: " + messageId
        );
    }
    
    /**
     * Crea una instancia para errores de consumo de mensajes.
     * 
     * @param topic Tópico de Kafka
     * @param consumerGroup Grupo de consumidores
     * @param cause Excepción original
     * @return Instancia de KafkaProcessingException
     */
    public static KafkaProcessingException consumerError(
            String topic, String consumerGroup, Throwable cause) {
        return new KafkaProcessingException(
            "Error al consumir mensajes de Kafka",
            cause,
            OperationType.CONSUME,
            topic,
            "Consumer Group: " + consumerGroup
        );
    }
    
    /**
     * Crea una instancia para errores de deserialización.
     * 
     * @param topic Tópico de Kafka
     * @param cause Excepción original
     * @return Instancia de KafkaProcessingException
     */
    public static KafkaProcessingException deserializationError(String topic, Throwable cause) {
        return new KafkaProcessingException(
            "Error al deserializar mensaje de Kafka",
            cause,
            OperationType.DESERIALIZE,
            topic
        );
    }
    
    /**
     * Crea una instancia para errores de serialización.
     * 
     * @param topic Tópico de Kafka
     * @param cause Excepción original
     * @return Instancia de KafkaProcessingException
     */
    public static KafkaProcessingException serializationError(String topic, Throwable cause) {
        return new KafkaProcessingException(
            "Error al serializar mensaje para Kafka",
            cause,
            OperationType.SERIALIZE,
            topic
        );
    }
    
    /**
     * Crea una instancia para errores en transacciones.
     * 
     * @param transactionId ID de la transacción
     * @param cause Excepción original
     * @return Instancia de KafkaProcessingException
     */
    public static KafkaProcessingException transactionError(String transactionId, Throwable cause) {
        return new KafkaProcessingException(
            "Error en transacción Kafka",
            cause,
            OperationType.TRANSACTION,
            null,
            "Transaction ID: " + transactionId
        );
    }
    
    /**
     * Crea una instancia para errores en el callback de Kafka.
     * 
     * @param topic Tópico de Kafka
     * @param cause Excepción original
     * @return Instancia de KafkaProcessingException
     */
    public static KafkaProcessingException callbackError(String topic, Throwable cause) {
        return new KafkaProcessingException(
            "Error en callback de Kafka",
            cause,
            OperationType.CALLBACK,
            topic
        );
    }
    
    /**
     * Crea una instancia genérica para otros errores de Kafka.
     * 
     * @param message Mensaje descriptivo
     * @param kafkaException Excepción original de Kafka
     * @return Instancia de KafkaProcessingException
     */
    public static KafkaProcessingException fromKafkaException(
            String message, KafkaException kafkaException) {
        return new KafkaProcessingException(
            message,
            kafkaException,
            OperationType.OTHER,
            null
        );
    }
    
    /**
     * Tipos de operación Kafka que pueden causar errores.
     */
    public enum OperationType {
        /**
         * Error al establecer conexión con servidores Kafka
         */
        CONNECTION,
        
        /**
         * Error al producir mensajes
         */
        PRODUCE,
        
        /**
         * Error al consumir mensajes
         */
        CONSUME,
        
        /**
         * Error al deserializar mensajes recibidos
         */
        DESERIALIZE,
        
        /**
         * Error al serializar mensajes para envío
         */
        SERIALIZE,
        
        /**
         * Error en transacciones Kafka
         */
        TRANSACTION,
        
        /**
         * Error en callbacks (ACK, NACK)
         */
        CALLBACK,
        
        /**
         * Error en administración de tópicos
         */
        ADMIN,
        
        /**
         * Otros tipos de error
         */
        OTHER
    }
}