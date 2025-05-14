package com.sg.webhookservice.kafka.consumer;

import com.sg.webhookservice.service.MessageSenderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Consumidor de Kafka para mensajes de webhook.
 * Se encarga de recibir mensajes de Kafka y procesarlos.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class WebhookEventConsumer {

    private final MessageSenderService messageSenderService;

    @Value("${app.kafka.consumer.enabled:true}")
    private boolean consumerEnabled;

    @Value("${app.kafka.topic-name:webhook-events}")
    private String webhookEventsTopic;

    @Value("${app.kafka.retry-topic-name:webhook-retries}")
    private String webhookRetriesTopic;

    /**
     * Listener principal para el topic de eventos webhook.
     * Recibe el ID del mensaje y lo procesa de forma asíncrona.
     *
     * @param messageIdStr ID del mensaje como string
     * @param partition Partición de Kafka (para logging)
     * @param offset Offset de Kafka (para logging)
     * @param ack Objeto para confirmar recepción del mensaje
     */
    @KafkaListener(
            topics = "${app.kafka.topic-name:webhook-events}",
            groupId = "${spring.kafka.consumer.group-id:webhook-processor-group}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeWebhookEvent(
            @Payload String messageIdStr,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {

        if (!consumerEnabled) {
            log.debug("Consumidor deshabilitado, confirmando mensaje sin procesar: {}", messageIdStr);
            ack.acknowledge();
            return;
        }

        log.info("Recibido mensaje de Kafka topic {} [partition: {}, offset: {}]: {}",
                webhookEventsTopic, partition, offset, messageIdStr);

        try {
            // Convertir string a UUID
            UUID messageId = UUID.fromString(messageIdStr);

            // Enviar para procesamiento asíncrono
            CompletableFuture<Boolean> processFuture = messageSenderService.processMessageAsync(messageId);

            // Confirmar recepción después del procesamiento exitoso
            processFuture.whenComplete((success, ex) -> {
                if (ex != null) {
                    log.error("Error procesando mensaje {}: {}", messageId, ex.getMessage(), ex);
                }

                // Confirmar recepción incluso si hay error, ya que el error se maneja en la BD
                ack.acknowledge();
                log.debug("Mensaje {} confirmado en Kafka", messageId);
            });

        } catch (IllegalArgumentException e) {
            // UUID inválido
            log.error("ID de mensaje inválido: {}", messageIdStr);
            ack.acknowledge(); // Confirmar para no bloquear el consumidor
        } catch (Exception e) {
            log.error("Error en consumidor Kafka: {}", e.getMessage(), e);
            ack.acknowledge(); // Confirmar para evitar reprocesamiento infinito
        }
    }

    /**
     * Listener para el topic de reintentos.
     * Maneja los mensajes que fallaron y necesitan ser reintentados.
     *
     * @param messageIdStr ID del mensaje como string
     * @param partition Partición de Kafka (para logging)
     * @param offset Offset de Kafka (para logging)
     * @param ack Objeto para confirmar recepción del mensaje
     */
    @KafkaListener(
            topics = "${app.kafka.retry-topic-name:webhook-retries}",
            groupId = "${spring.kafka.consumer.group-id:webhook-processor-group}-retry",
            containerFactory = "retryKafkaListenerContainerFactory"
    )
    public void consumeWebhookRetry(
            @Payload String messageIdStr,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {

        if (!consumerEnabled) {
            log.debug("Consumidor de reintentos deshabilitado, confirmando mensaje sin procesar: {}", messageIdStr);
            ack.acknowledge();
            return;
        }

        log.info("Recibido mensaje de reintento de Kafka topic {} [partition: {}, offset: {}]: {}",
                webhookRetriesTopic, partition, offset, messageIdStr);

        try {
            // Convertir string a UUID
            UUID messageId = UUID.fromString(messageIdStr);

            // Enviar para procesamiento asíncrono
            CompletableFuture<Boolean> processFuture = messageSenderService.processMessageAsync(messageId);

            // Confirmar recepción después del procesamiento exitoso
            processFuture.whenComplete((success, ex) -> {
                if (ex != null) {
                    log.error("Error procesando reintento {}: {}", messageId, ex.getMessage(), ex);
                }

                // Confirmar recepción incluso si hay error, ya que el error se maneja en la BD
                ack.acknowledge();
                log.debug("Mensaje de reintento {} confirmado en Kafka", messageId);
            });

        } catch (IllegalArgumentException e) {
            // UUID inválido
            log.error("ID de mensaje de reintento inválido: {}", messageIdStr);
            ack.acknowledge(); // Confirmar para no bloquear el consumidor
        } catch (Exception e) {
            log.error("Error en consumidor de reintentos Kafka: {}", e.getMessage(), e);
            ack.acknowledge(); // Confirmar para evitar reprocesamiento infinito
        }
    }
}
