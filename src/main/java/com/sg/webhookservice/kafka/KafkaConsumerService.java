package com.sg.webhookservice.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sg.webhookservice.service.MessageProcessingService;
import com.sg.webhookservice.exception.KafkaProcessingException;
import com.sg.webhookservice.service.MessageProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * Servicio que consume mensajes de los tópicos Kafka para procesamiento de webhooks.
 * 
 * Este servicio:
 * - Escucha los tópicos webhook-events y webhook-retries
 * - Procesa los mensajes recibidos utilizando MessageProcessingService
 * - Maneja los errores y acknowledgments para garantizar la entrega confiable
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class KafkaConsumerService {

    private final MessageProcessingService messageProcessingService;
    private final ObjectMapper objectMapper;
    
    @Value("${kafka.topic.webhook-events:webhook-events}")
    private String webhookEventsTopic;
    
    @Value("${kafka.topic.webhook-retries:webhook-retries}")
    private String webhookRetriesTopic;
    
    @Value("${kafka.consumer.group-id:webhook-processor-group}")
    private String consumerGroupId;
    
    @Value("${kafka.consumer.retry-group-id:webhook-retry-processor-group}")
    private String retryConsumerGroupId;
    
    /**
     * Escucha eventos regulares de webhook en el tópico principal.
     * 
     * @param payload Payload del mensaje en formato JSON
     * @param key Clave del mensaje (opcional)
     * @param partition Partición de donde proviene el mensaje
     * @param topic Tópico del mensaje
     * @param acknowledgment Objeto para confirmar el procesamiento
     */
    @KafkaListener(
        topics = "${kafka.topic.webhook-events:webhook-events}",
        groupId = "${kafka.consumer.group-id:webhook-processor-group}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeWebhookEvent(
            @Payload String payload,
            @Header(name = KafkaHeaders.RECEIVED_KEY, required = false) String key,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            Acknowledgment acknowledgment) {
        
        log.info("Recibido mensaje en tópico {}, partición {}", topic, partition);
        
        try {
            // Procesar el payload
            Map<String, Object> messageData = parseMessageData(payload);
            
            // Extraer ID del mensaje
            String messageId = (String) messageData.get("message_id");
            if (messageId == null || messageId.isEmpty()) {
                throw new KafkaProcessingException(
                    "Mensaje Kafka sin ID de mensaje",
                    KafkaProcessingException.OperationType.DESERIALIZE,
                    topic
                );
            }
            
            log.info("Procesando mensaje de webhook con ID: {}", messageId);
            
            // Procesar el mensaje
            messageProcessingService.processMessage(UUID.fromString(messageId));
            
            // Confirmar procesamiento exitoso
            acknowledgment.acknowledge();
            log.info("Mensaje procesado y confirmado correctamente: {}", messageId);
            
        } catch (Exception e) {
            // En caso de error, registrar pero confirmar de todos modos
            // para evitar procesamiento repetido de mensajes con error
            log.error("Error procesando mensaje de Kafka: {}", e.getMessage(), e);
            
            // Confirmar el mensaje para evitar bucles infinitos
            // La estrategia de reintentos está manejada a nivel de webhook, no de Kafka
            acknowledgment.acknowledge();
            
            // Si es un error grave que queremos propagar
            if (e instanceof KafkaProcessingException) {
                throw (KafkaProcessingException) e;
            }
        }
    }
    
    /**
     * Escucha eventos de reintento de webhook en el tópico de reintentos.
     * 
     * @param payload Payload del mensaje en formato JSON
     * @param partition Partición de donde proviene el mensaje
     * @param topic Tópico del mensaje
     * @param acknowledgment Objeto para confirmar el procesamiento
     */
    @KafkaListener(
        topics = "${kafka.topic.webhook-retries:webhook-retries}",
        groupId = "${kafka.consumer.retry-group-id:webhook-retry-processor-group}",
        containerFactory = "retryKafkaListenerContainerFactory"
    )
    public void consumeWebhookRetry(
            @Payload String payload,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            Acknowledgment acknowledgment) {
        
        log.info("Recibido mensaje de reintento en tópico {}, partición {}", topic, partition);
        
        try {
            // Procesar el payload
            Map<String, Object> messageData = parseMessageData(payload);
            
            // Extraer ID del mensaje
            String messageId = (String) messageData.get("message_id");
            if (messageId == null || messageId.isEmpty()) {
                throw new KafkaProcessingException(
                    "Mensaje Kafka de reintento sin ID de mensaje",
                    KafkaProcessingException.OperationType.DESERIALIZE,
                    topic
                );
            }
            
            log.info("Procesando reintento de webhook con ID: {}", messageId);
            
            // Procesar el reintento
            messageProcessingService.processRetry(UUID.fromString(messageId));
            
            // Confirmar procesamiento exitoso
            acknowledgment.acknowledge();
            log.info("Reintento procesado y confirmado correctamente: {}", messageId);
            
        } catch (Exception e) {
            // En caso de error, registrar pero confirmar de todos modos
            log.error("Error procesando reintento de Kafka: {}", e.getMessage(), e);
            
            // Confirmar el mensaje para evitar bucles infinitos
            acknowledgment.acknowledge();
            
            // Si es un error grave que queremos propagar
            if (e instanceof KafkaProcessingException) {
                throw (KafkaProcessingException) e;
            }
        }
    }
    
    /**
     * Escucha el tópico de balanceo de cargas para distribuir mensajes entre nodos.
     * Esto es útil en despliegues con múltiples instancias del servicio.
     * 
     * @param payload Payload del mensaje en formato JSON
     * @param acknowledgment Objeto para confirmar el procesamiento
     */
    @KafkaListener(
        topics = "${kafka.topic.webhook-balancing:webhook-balancing}",
        groupId = "${kafka.consumer.balancing-group-id:webhook-balancing-group}",
        containerFactory = "kafkaListenerContainerFactory",
        autoStartup = "${kafka.balancing.enabled:false}"
    )
    public void consumeBalancingMessage(
            @Payload String payload,
            Acknowledgment acknowledgment) {
        
        log.info("Recibido mensaje de balanceo de carga");
        
        try {
            // Procesar el payload
            Map<String, Object> messageData = parseMessageData(payload);
            
            // Extraer ID del mensaje y tipo de operación
            String messageId = (String) messageData.get("message_id");
            String operationType = (String) messageData.get("operation");
            
            if (messageId == null || messageId.isEmpty()) {
                throw new IllegalArgumentException("Mensaje de balanceo sin ID de mensaje");
            }
            
            // Determinar la operación basada en el tipo
            if ("process".equals(operationType)) {
                messageProcessingService.processMessage(UUID.fromString(messageId));
            } else if ("retry".equals(operationType)) {
                messageProcessingService.processRetry(UUID.fromString(messageId));
            } else {
                log.warn("Operación desconocida en mensaje de balanceo: {}", operationType);
            }
            
            // Confirmar procesamiento
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Error procesando mensaje de balanceo: {}", e.getMessage(), e);
            acknowledgment.acknowledge();
        }
    }
    
    /**
     * Parsea los datos del mensaje de un string JSON.
     * 
     * @param payload Payload en formato JSON
     * @return Mapa con los datos del mensaje
     * @throws KafkaProcessingException Si hay error al deserializar
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseMessageData(String payload) throws KafkaProcessingException {
        try {
            return objectMapper.readValue(payload, Map.class);
        } catch (Exception e) {
            throw new KafkaProcessingException(
                "Error al deserializar mensaje Kafka",
                e,
                KafkaProcessingException.OperationType.DESERIALIZE,
                "unknown",
                null,
                "Payload: " + (payload != null ? payload.substring(0, Math.min(payload.length(), 100)) : "null")
            );
        }
    }
    
    /**
     * Método para verificar la conectividad con Kafka.
     * Utilizado por health checks.
     * 
     * @return true si la conexión está activa
     */
    public boolean isKafkaConnected() {
        // Esta es una verificación simplificada
        // Una verificación más completa podría intentar publicar y consumir un mensaje de prueba
        try {
            return true; // En una implementación real, verifique el estado de los consumidores
        } catch (Exception e) {
            log.warn("Error verificando conectividad con Kafka: {}", e.getMessage());
            return false;
        }
    }
}