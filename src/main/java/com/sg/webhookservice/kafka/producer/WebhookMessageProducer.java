package com.sg.webhookservice.kafka.producer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureCallback;

import java.util.concurrent.CompletableFuture;

/**
 * Productor de mensajes Kafka para el servicio de webhooks.
 * Se encarga de enviar mensajes a Kafka para su procesamiento asíncrono.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class WebhookMessageProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${app.kafka.producer.enabled:true}")
    private boolean producerEnabled;

    @Value("${app.kafka.producer.ack-timeout-ms:10000}")
    private long ackTimeoutMs;

    /**
     * Envía un mensaje a Kafka.
     *
     * @param topic Tema de Kafka
     * @param message Mensaje a enviar
     * @return true si el envío fue exitoso o si el productor está deshabilitado, false en caso de error
     */
    public boolean sendMessage(String topic, String message) {
        // Si el productor está deshabilitado, simplemente retornar éxito
        if (!producerEnabled) {
            log.debug("Productor Kafka deshabilitado, omitiendo envío a {}: {}", topic, message);
            return true;
        }

        try {
            log.debug("Enviando mensaje a Kafka topic {}: {}", topic, message);

            // En Spring Boot 3+ (con Kafka más reciente)
            CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(topic, message);

            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    // Éxito
                    log.debug("Mensaje enviado a {} [partition: {}, offset: {}]: {}",
                            topic,
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset(),
                            message);
                } else {
                    // Error
                    log.error("Error enviando mensaje a {}: {}", topic, ex.getMessage(), ex);
                }
            });

            // Esperar el resultado con timeout
            future.get(ackTimeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            return true;

        } catch (Exception e) {
            log.error("Error enviando mensaje a Kafka topic {}: {}", topic, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Envía un mensaje a Kafka y maneja notificaciones de forma asíncrona.
     *
     * @param topic Tema de Kafka
     * @param message Mensaje a enviar
     */
    public void sendMessageAsync(String topic, String message) {
        // Si el productor está deshabilitado, simplemente retornar
        if (!producerEnabled) {
            log.debug("Productor Kafka deshabilitado, omitiendo envío a {}: {}", topic, message);
            return;
        }

        try {
            log.debug("Enviando mensaje asíncrono a Kafka topic {}: {}", topic, message);

            // En Spring Boot 3+ (con Kafka más reciente)
            CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(topic, message);

            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    // Éxito
                    log.debug("Mensaje enviado a {} [partition: {}, offset: {}]: {}",
                            topic,
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset(),
                            message);
                } else {
                    // Error
                    log.error("Error enviando mensaje a {}: {}", topic, ex.getMessage(), ex);
                }
            });

        } catch (Exception e) {
            log.error("Error enviando mensaje asíncrono a Kafka topic {}: {}", topic, e.getMessage(), e);
        }
    }

    /**
     * Versión del productor compatible con Spring Boot 2.x.
     * Mantiene compatibilidad con versiones anteriores.
     *
     * @param topic Tema de Kafka
     * @param message Mensaje a enviar
     * @return true si el envío fue exitoso o si el productor está deshabilitado, false en caso de error
     */
    public boolean sendMessageLegacy(String topic, String message) {
        // Si el productor está deshabilitado, simplemente retornar éxito
        if (!producerEnabled) {
            log.debug("Productor Kafka deshabilitado, omitiendo envío a {}: {}", topic, message);
            return true;
        }

        try {
            log.debug("Enviando mensaje a Kafka (modo legacy) topic {}: {}", topic, message);

            // En Spring Boot 2.x
            ListenableFuture<SendResult<String, String>> future = kafkaTemplate.send(topic, message);

            future.addCallback(new ListenableFutureCallback<>() {
                @Override
                public void onSuccess(SendResult<String, String> result) {
                    log.debug("Mensaje enviado a {} [partition: {}, offset: {}]: {}",
                            topic,
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset(),
                            message);
                }

                @Override
                public void onFailure(Throwable ex) {
                    log.error("Error enviando mensaje a {}: {}", topic, ex.getMessage(), ex);
                }
            });

            // Esperar el resultado con timeout
            future.get(ackTimeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            return true;

        } catch (Exception e) {
            log.error("Error enviando mensaje a Kafka topic {}: {}", topic, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Envía un mensaje con una clave específica para garantizar
     * que mensajes con la misma clave vayan a la misma partición.
     *
     * @param topic Tema de Kafka
     * @param key Clave para particionamiento
     * @param message Mensaje a enviar
     * @return true si el envío fue exitoso, false en caso de error
     */
    public boolean sendMessageWithKey(String topic, String key, String message) {
        if (!producerEnabled) {
            log.debug("Productor Kafka deshabilitado, omitiendo envío a {}: {}", topic, message);
            return true;
        }

        try {
            log.debug("Enviando mensaje con clave '{}' a Kafka topic {}: {}", key, topic, message);

            CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(topic, key, message);

            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.debug("Mensaje con clave '{}' enviado a {} [partition: {}, offset: {}]: {}",
                            key,
                            topic,
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset(),
                            message);
                } else {
                    log.error("Error enviando mensaje con clave '{}' a {}: {}",
                            key, topic, ex.getMessage(), ex);
                }
            });

            // Esperar el resultado con timeout
            future.get(ackTimeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            return true;

        } catch (Exception e) {
            log.error("Error enviando mensaje con clave '{}' a Kafka topic {}: {}",
                    key, topic, e.getMessage(), e);
            return false;
        }
    }
}