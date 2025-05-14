package com.sg.webhookservice.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sg.webhookservice.exception.KafkaProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Servicio responsable de publicar mensajes en los tópicos Kafka.
 * 
 * Este servicio:
 * - Envía mensajes a los tópicos webhook-events y webhook-retries
 * - Maneja la serialización de mensajes a formato JSON
 * - Proporciona confirmación asíncrona de la entrega de mensajes
 * - Gestiona errores de publicación y timeouts
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class KafkaProducerService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    
    @Value("${kafka.topic.webhook-events:webhook-events}")
    private String webhookEventsTopic;
    
    @Value("${kafka.topic.webhook-retries:webhook-retries}")
    private String webhookRetriesTopic;
    
    @Value("${kafka.topic.webhook-balancing:webhook-balancing}")
    private String webhookBalancingTopic;
    
    @Value("${kafka.producer.send-timeout-ms:5000}")
    private long sendTimeoutMs;
    
    @Value("${kafka.producer.sync-send:false}")
    private boolean syncSend;
    
    @Value("${app.direct-mode:false}")
    private boolean directMode;
    
    /**
     * Envía un mensaje de webhook al tópico principal de eventos.
     * 
     * @param messageId ID del mensaje a procesar
     * @return Resultado de la operación (éxito o fallo)
     * @throws KafkaProcessingException Si hay un error al publicar el mensaje
     */
    public boolean sendWebhookMessage(String messageId) {
        // Si estamos en modo directo, no usar Kafka
        if (directMode) {
            log.info("En modo directo - ignorando solicitud de envío a Kafka para mensaje: {}", messageId);
            return true;
        }
        
        Map<String, Object> messageData = createMessagePayload(messageId);
        return sendToKafka(webhookEventsTopic, messageId, messageData);
    }
    
    /**
     * Envía un mensaje al tópico de reintentos.
     * 
     * @param messageId ID del mensaje a reintentar
     * @return Resultado de la operación (éxito o fallo)
     * @throws KafkaProcessingException Si hay un error al publicar el mensaje
     */
    public boolean sendRetryMessage(String messageId) {
        // Si estamos en modo directo, no usar Kafka
        if (directMode) {
            log.info("En modo directo - ignorando solicitud de reintento vía Kafka para mensaje: {}", messageId);
            return true;
        }
        
        Map<String, Object> messageData = createMessagePayload(messageId);
        return sendToKafka(webhookRetriesTopic, messageId, messageData);
    }
    
    /**
     * Envía un mensaje al tópico de balanceo de cargas.
     * Útil en despliegues con múltiples instancias para distribuir la carga.
     * 
     * @param messageId ID del mensaje a procesar
     * @param operation Tipo de operación ("process" o "retry")
     * @param targetNode Nodo específico al que dirigir el mensaje (opcional)
     * @return Resultado de la operación (éxito o fallo)
     */
    public boolean sendBalancingMessage(String messageId, String operation, String targetNode) {
        // Si estamos en modo directo, no usar Kafka
        if (directMode) {
            log.info("En modo directo - ignorando solicitud de balanceo para mensaje: {}", messageId);
            return true;
        }
        
        Map<String, Object> messageData = createMessagePayload(messageId);
        messageData.put("operation", operation);
        
        if (targetNode != null && !targetNode.isEmpty()) {
            messageData.put("target_node", targetNode);
        }
        
        String key = targetNode != null ? targetNode : messageId;
        return sendToKafka(webhookBalancingTopic, key, messageData);
    }
    
    /**
     * Crea el payload base para un mensaje Kafka.
     * 
     * @param messageId ID del mensaje
     * @return Mapa con los datos del mensaje
     */
    private Map<String, Object> createMessagePayload(String messageId) {
        Map<String, Object> messageData = new HashMap<>();
        messageData.put("message_id", messageId);
        messageData.put("timestamp", System.currentTimeMillis());
        messageData.put("uuid", UUID.randomUUID().toString());
        return messageData;
    }
    
    /**
     * Envía un mensaje a un tópico Kafka específico.
     * 
     * @param topic Tópico destino
     * @param key Clave para particionamiento (típicamente el ID del mensaje)
     * @param messageData Datos del mensaje
     * @return Resultado de la operación (éxito o fallo)
     * @throws KafkaProcessingException Si hay un error al publicar el mensaje
     */
    private boolean sendToKafka(String topic, String key, Map<String, Object> messageData) {
        try {
            // Serializar el mensaje a JSON
            String payload = objectMapper.writeValueAsString(messageData);
            
            log.debug("Enviando a Kafka - Tópico: {}, Key: {}, Payload: {}", 
                    topic, key, payload.substring(0, Math.min(payload.length(), 100)));
            
            // Intentar enviar el mensaje
            if (syncSend) {
                // Envío síncrono
                try {
                    CompletableFuture<SendResult<String, String>> future = 
                            kafkaTemplate.send(topic, key, payload);
                    
                    // Esperar por el resultado con timeout
                    SendResult<String, String> result = 
                            future.get(sendTimeoutMs, TimeUnit.MILLISECONDS);
                    
                    log.debug("Mensaje enviado a Kafka (síncrono) - Tópico: {}, Partición: {}, Offset: {}", 
                            result.getRecordMetadata().topic(),
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());
                    
                    return true;
                    
                } catch (TimeoutException e) {
                    throw new KafkaProcessingException(
                        "Timeout al enviar mensaje a Kafka",
                        e,
                        KafkaProcessingException.OperationType.PRODUCE,
                        topic,
                        String.format("Key: %s, Timeout: %d ms", key, sendTimeoutMs)
                    );
                } catch (Exception e) {
                    throw new KafkaProcessingException(
                        "Error al enviar mensaje a Kafka",
                        e,
                        KafkaProcessingException.OperationType.PRODUCE,
                        topic
                    );
                }
            } else {
                // Envío asíncrono con callback
                CompletableFuture<SendResult<String, String>> future = 
                        kafkaTemplate.send(topic, key, payload);
                
                future.whenComplete((result, ex) -> {
                    if (ex == null) {
                        // Éxito
                        log.debug("Mensaje enviado a Kafka (asíncrono) - Tópico: {}, Partición: {}, Offset: {}", 
                                result.getRecordMetadata().topic(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    } else {
                        // Error
                        log.error("Error al enviar mensaje a Kafka (asíncrono) - Tópico: {}, Key: {}, Error: {}", 
                                topic, key, ex.getMessage(), ex);
                    }
                });
                
                return true;
            }
            
        } catch (KafkaProcessingException e) {
            // Re-lanzar excepciones específicas de Kafka
            throw e;
        } catch (Exception e) {
            // Capturar y convertir otras excepciones
            throw new KafkaProcessingException(
                "Error inesperado al enviar mensaje a Kafka",
                e,
                KafkaProcessingException.OperationType.PRODUCE,
                topic,
                key
            );
        }
    }
    
    /**
     * Envía un mensaje de prueba para verificar la conectividad con Kafka.
     * Utilizado por health checks.
     * 
     * @return true si el mensaje se envió correctamente
     */
    public boolean testKafkaConnection() {
        try {
            // Crear un mensaje de prueba con ID único
            String testMessageId = "test-" + UUID.randomUUID();
            Map<String, Object> testData = createMessagePayload(testMessageId);
            testData.put("test", true);
            
            // Intentar enviar a un tópico temporal
            String testTopic = webhookEventsTopic + "-test";
            String payload = objectMapper.writeValueAsString(testData);
            
            // Envío síncrono para el test
            CompletableFuture<SendResult<String, String>> future = 
                    kafkaTemplate.send(testTopic, testMessageId, payload);
            
            // Esperar con timeout reducido para test
            future.get(2000, TimeUnit.MILLISECONDS);
            
            return true;
        } catch (Exception e) {
            log.warn("Test de conexión a Kafka fallido: {}", e.getMessage());
            return false;
        }
    }
}