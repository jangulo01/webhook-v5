package com.sg.webhookservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sg.webhookservice.dto.DeliveryAttemptDto;
import com.sg.webhookservice.dto.MessageDto;
import com.sg.webhookservice.exception.ResourceNotFoundException;
import com.sg.webhookservice.exception.WebhookProcessingException;
import com.sg.webhookservice.model.DeliveryAttempt;
import com.sg.webhookservice.model.Message;
import com.sg.webhookservice.model.Message.MessageStatus;
import com.sg.webhookservice.model.WebhookConfig;
import com.sg.webhookservice.repository.DeliveryAttemptRepository;
import com.sg.webhookservice.repository.MessageRepository;
import com.sg.webhookservice.repository.WebhookConfigRepository;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Servicio central para procesamiento de mensajes de webhook.
 *
 * Maneja todo el ciclo de vida del procesamiento de mensajes, incluyendo:
 * - Procesamiento inicial de mensajes
 * - Entrega a destinos
 * - Manejo de reintentos
 * - Gestión de errores y excepciones
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MessageProcessingService {

    private final MessageRepository messageRepository;
    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final WebhookConfigRepository webhookConfigRepository;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final RetryService retryService;
    private final HealthMonitoringService healthMonitoringService;

    @Value("${app.processing.connection-timeout-ms:5000}")
    private int connectionTimeoutMs;

    @Value("${app.processing.read-timeout-ms:10000}")
    private int readTimeoutMs;

    @Value("${app.processing.max-payload-log-length:1000}")
    private int maxPayloadLogLength;

    @Value("${app.processing.node-identifier:#{null}}")
    private String nodeIdentifier;

    /**
     * Procesa un mensaje recién recibido o previamente pendiente.
     *
     * @param messageId ID del mensaje a procesar
     * @throws WebhookProcessingException Si ocurre error en el procesamiento
     * @throws ResourceNotFoundException Si el mensaje no existe
     */
    @Transactional
    public void processMessage(@NotNull UUID messageId) {
        log.info("Procesando mensaje: {}", messageId);

        // Obtener mensaje con bloqueo para actualización
        Message message = getMessage(messageId);

        try {
            // Intentar marcar como en procesamiento
            int updated = messageRepository.markAsProcessing(messageId);
            if (updated == 0) {
                log.warn("No se pudo marcar mensaje {} como en procesamiento, posible estado incorrecto", messageId);
                // Verificar estado actual para diagnóstico
                log.info("Estado actual del mensaje {}: {}", messageId, message.getStatus());
                return;
            }

            // Verificar que el webhook esté activo
            WebhookConfig config = message.getWebhookConfig();
            if (!config.isActive()) {
                log.warn("Webhook {} está inactivo, cancelando mensaje {}",
                        config.getName(), messageId);
                messageRepository.cancelMessage(messageId);
                return;
            }

            // Enviar mensaje a destino
            deliverMessage(message);

        } catch (WebhookProcessingException e) {
            // Propagar excepciones de webhook sin envolver
            throw e;
        } catch (Exception e) {
            log.error("Error procesando mensaje {}: {}", messageId, e.getMessage(), e);
            throw new WebhookProcessingException(
                    "Error en procesamiento de mensaje",
                    e,
                    WebhookProcessingException.ProcessingPhase.PREPARATION,
                    message.getWebhookConfig().getName(),
                    messageId.toString()
            );
        }
    }

    /**
     * Busca y procesa mensajes pendientes manualmente.
     *
     * @return El número total de mensajes pendientes procesados
     */
    public int processPendingMessages() {
        log.info("Verificando mensajes pendientes...");
        int totalProcessed = 0;

        try {
            // 1. Procesar mensajes pendientes en la base de datos
            List<UUID> pendingMessageIds = messageRepository.findPendingMessageIds();
            log.info("Encontrados {} mensajes pendientes en base de datos", pendingMessageIds.size());

            for (UUID messageId : pendingMessageIds) {
                try {
                    log.info("Procesando mensaje pendiente: {}", messageId);
                    processMessage(messageId);
                    totalProcessed++;
                } catch (Exception e) {
                    log.error("Error procesando mensaje pendiente {}: {}",
                            messageId, e.getMessage(), e);
                }
            }

            // 2. Procesar mensajes con estado 'failed' que deberían reintentarse
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            List<UUID> retryMessageIds = messageRepository.findRetryMessageIds(now);
            log.info("Encontrados {} mensajes listos para reintento", retryMessageIds.size());

            for (UUID messageId : retryMessageIds) {
                try {
                    log.info("Procesando reintento para mensaje: {}", messageId);
                    processRetry(messageId);
                    totalProcessed++;
                } catch (Exception e) {
                    log.error("Error procesando reintento {}: {}",
                            messageId, e.getMessage(), e);
                }
            }

            log.info("Verificación de mensajes pendientes completada. Total procesados: {}", totalProcessed);
            return totalProcessed;

        } catch (Exception e) {
            log.error("Error general procesando mensajes pendientes: {}", e.getMessage(), e);
            return totalProcessed; // Devolver los procesados hasta el momento
        }
    }

    /**
     * Procesa un reintento de mensaje fallido.
     *
     * @param messageId ID del mensaje a reintentar
     * @throws WebhookProcessingException Si ocurre error en el procesamiento
     * @throws ResourceNotFoundException Si el mensaje no existe
     */
    @Transactional
    public void processRetry(@NotNull UUID messageId) {
        log.info("Procesando reintento para mensaje: {}", messageId);

        // Obtener mensaje
        Message message = getMessage(messageId);

        // Verificar que esté en estado adecuado para reintento
        if (message.getStatus() != MessageStatus.FAILED || message.getNextRetry() == null) {
            log.warn("Mensaje {} no está en estado adecuado para reintento. Estado: {}, NextRetry: {}",
                    messageId, message.getStatus(), message.getNextRetry());
            return;
        }

        // Verificar que sea tiempo de reintentar
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (message.getNextRetry().isAfter(now)) {
            log.info("Aún no es tiempo de reintentar mensaje {}. Programado para: {}",
                    messageId, message.getNextRetry());
            return;
        }

        try {
            // Marcar como en procesamiento
            int updated = messageRepository.markAsProcessing(messageId);
            if (updated == 0) {
                log.warn("No se pudo marcar mensaje {} para reintento, posible concurrencia", messageId);
                return;
            }

            // Verificar si ya excedió el máximo de reintentos
            if (message.hasExceededMaxRetries()) {
                log.warn("Mensaje {} ha excedido el máximo de reintentos ({}), marcando como fallido definitivo",
                        messageId, message.getRetryCount());

                // Marcar como fallido sin más reintentos
                messageRepository.markAsFailed(
                        messageId,
                        "Máximo de reintentos excedido",
                        null
                );

                // Actualizar estadísticas de salud
                healthMonitoringService.recordFailedDelivery(message.getWebhookConfig().getId());

                return;
            }

            // Verificar que el webhook siga activo
            WebhookConfig config = message.getWebhookConfig();
            if (!config.isActive()) {
                log.warn("Webhook {} está inactivo, cancelando reintento {}",
                        config.getName(), messageId);
                messageRepository.cancelMessage(messageId);
                return;
            }

            // Incrementar contador de reintentos
            messageRepository.incrementRetryCount(messageId);

            // Entregar mensaje
            deliverMessage(message);

        } catch (WebhookProcessingException e) {
            // Propagar excepciones de webhook
            throw e;
        } catch (Exception e) {
            log.error("Error procesando reintento {}: {}", messageId, e.getMessage(), e);
            throw new WebhookProcessingException(
                    "Error en procesamiento de reintento",
                    e,
                    WebhookProcessingException.ProcessingPhase.RETRY_SCHEDULING,
                    message.getWebhookConfig().getName(),
                    messageId.toString()
            );
        }
    }

    /**
     * Entrega un mensaje a su destino.
     *
     * @param message Mensaje a entregar
     * @throws WebhookProcessingException Si ocurre error en la entrega
     */
    private void deliverMessage(Message message) {
        UUID messageId = message.getId();
        WebhookConfig config = message.getWebhookConfig();
        String webhookName = config.getName();
        String targetUrl = message.getTargetUrl();

        log.info("Enviando mensaje {} del webhook {} a URL: {}",
                messageId, webhookName, targetUrl);

        // Crear intento de entrega
        DeliveryAttempt attempt = new DeliveryAttempt();
        attempt.setMessage(message);
        attempt.setAttemptNumber(message.getRetryCount() + 1);
        attempt.setTimestamp(OffsetDateTime.now(ZoneOffset.UTC));
        attempt.setTargetUrl(targetUrl);

        // Agregar identificador de nodo si está configurado (para deployments multi-instancia)
        if (nodeIdentifier != null && !nodeIdentifier.isEmpty()) {
            attempt.setProcessingNode(nodeIdentifier);
        }

        try {
            // Preparar headers
            HttpHeaders headers = prepareHeaders(message);

            // Loguear intento (truncando payload si es muy largo)
            String truncatedPayload = truncateForLog(message.getPayload());
            log.debug("Enviando payload: {} con headers: {}", truncatedPayload, headers);

            // Registrar tiempo de inicio
            long startTime = System.currentTimeMillis();

            // Realizar solicitud HTTP al destino
            org.springframework.http.HttpEntity<String> entity =
                    new org.springframework.http.HttpEntity<>(message.getPayload(), headers);

            org.springframework.http.ResponseEntity<String> response =
                    restTemplate.postForEntity(targetUrl, entity, String.class);

            // Calcular duración
            long duration = System.currentTimeMillis() - startTime;

            // Registrar respuesta
            attempt.setStatusCode(response.getStatusCode().value());
            attempt.setResponseBody(truncateResponse(response.getBody()));
            attempt.setRequestDuration(duration);
            attempt.setResponseHeaders(convertHeadersToJson(response.getHeaders()));

            // Guardar intento
            deliveryAttemptRepository.save(attempt);

            // Procesar resultado según código de estado
            if (response.getStatusCode().is2xxSuccessful()) {
                // Éxito - marcar como entregado
                log.info("Mensaje {} entregado exitosamente a {} en {} ms",
                        messageId, targetUrl, duration);

                messageRepository.markAsDelivered(messageId);

                // Actualizar estadísticas de salud
                healthMonitoringService.recordSuccessfulDelivery(
                        config.getId(),
                        response.getStatusCode().value(),
                        duration
                );

            } else if (shouldRetry(response.getStatusCode().value())) {
                // Respuesta de error pero susceptible de reintento
                log.warn("Envío de mensaje {} falló con estado {}, se programará reintento",
                        messageId, response.getStatusCode());

                // Programar reintento
                scheduleRetry(message, attempt,
                        "Respuesta de error: HTTP " + response.getStatusCode().value());

                // Actualizar estadísticas de salud
                healthMonitoringService.recordFailedAttempt(
                        config.getName(),
                        response.getStatusCode().value(),
                        duration
                );

            } else {
                // Error permanente, no reintentar
                log.error("Envío de mensaje {} falló permanentemente con estado {}",
                        messageId, response.getStatusCode());

                // Marcar como fallido sin reintento
                messageRepository.markAsFailed(
                        messageId,
                        "Error permanente: HTTP " + response.getStatusCode().value(),
                        null
                );

                // Actualizar estadísticas de salud
                healthMonitoringService.recordFailedDelivery(config.getId());
            }

        } catch (SocketTimeoutException e) {
            // Timeout de conexión o respuesta
            log.warn("Timeout conectando a {} para mensaje {}: {}",
                    targetUrl, messageId, e.getMessage());

            attempt.setError("Timeout: " + e.getMessage());
            attempt.setRequestDuration((long) Math.max(connectionTimeoutMs, readTimeoutMs));
            deliveryAttemptRepository.save(attempt);

            // Programar reintento
            scheduleRetry(message, attempt, "Timeout de conexión");

            // Actualizar estadísticas
            healthMonitoringService.recordConnectionError(config.getName());

        } catch (Exception e) {
            // Otros errores
            log.error("Error enviando mensaje {} a {}: {}",
                    messageId, targetUrl, e.getMessage(), e);

            attempt.setError(e.getMessage());
            attempt.setRequestDuration(System.currentTimeMillis() - System.currentTimeMillis());
            deliveryAttemptRepository.save(attempt);

            // Determinar si el error es recuperable
            if (isRecoverableError(e)) {
                // Programar reintento
                scheduleRetry(message, attempt, "Error recuperable: " + e.getClass().getSimpleName());
            } else {
                // Error permanente
                messageRepository.markAsFailed(
                        messageId,
                        "Error permanente: " + e.getMessage(),
                        null
                );

                healthMonitoringService.recordFailedDelivery(config.getId());
            }
        }
    }

    /**
     * Prepara los headers HTTP para envío.
     *
     * @param message Mensaje con la información necesaria
     * @return Headers HTTP listos para envío
     */
    private HttpHeaders prepareHeaders(Message message) {
        HttpHeaders headers = new HttpHeaders();

        // Content-Type siempre JSON
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Agregar firma
        headers.add("X-Webhook-Signature", message.getSignature());

        // Agregar ID de mensaje para correlación
        headers.add("X-Webhook-ID", message.getId().toString());

        // Agregar reintento si es aplicable
        if (message.getRetryCount() > 0) {
            headers.add("X-Webhook-Retry-Count", String.valueOf(message.getRetryCount()));
        }

        // Agregar headers personalizados definidos en configuración
        if (message.getHeaders() != null && !message.getHeaders().isEmpty()) {
            try {
                Map<String, String> customHeaders = objectMapper.readValue(
                        message.getHeaders(),
                        objectMapper.getTypeFactory().constructMapType(
                                Map.class, String.class, String.class));

                for (Map.Entry<String, String> entry : customHeaders.entrySet()) {
                    headers.add(entry.getKey(), entry.getValue());
                }
            } catch (IOException e) {
                log.warn("Error parseando headers personalizados para mensaje {}: {}",
                        message.getId(), e.getMessage());
            }
        }

        return headers;
    }

    /**
     * Programa un reintento para un mensaje fallido.
     *
     * @param message Mensaje a reintentar
     * @param attempt Intento fallido
     * @param errorMessage Mensaje de error a registrar
     */
    private void scheduleRetry(Message message, DeliveryAttempt attempt, String errorMessage) {
        UUID messageId = message.getId();
        WebhookConfig config = message.getWebhookConfig();

        // Verificar si ya excedió máximo de reintentos
        if (message.hasExceededMaxRetries()) {
            log.warn("Mensaje {} ha excedió el máximo de reintentos ({}), no se reintentará",
                    messageId, message.getRetryCount());

            messageRepository.markAsFailed(
                    messageId,
                    errorMessage + " - Máximo de reintentos excedido",
                    null
            );

            return;
        }

        // Calcular tiempo de próximo reintento
        OffsetDateTime nextRetry = retryService.calculateNextRetryTime(
                message,
                attempt,
                config
        );

        // Registrar reintento
        messageRepository.markAsFailed(
                messageId,
                errorMessage,
                nextRetry
        );

        log.info("Mensaje {} programado para reintento en: {}", messageId, nextRetry);
    }

    /**
     * Determina si un código de estado HTTP amerita reintento.
     *
     * @param statusCode Código de estado HTTP
     * @return true si debería reintentarse, false si no
     */
    private boolean shouldRetry(int statusCode) {
        // Reintentar códigos 5xx (errores de servidor)
        if (statusCode >= 500 && statusCode < 600) {
            return true;
        }

        // Reintentar algunos códigos 4xx específicos
        return statusCode == 408 || // Request Timeout
                statusCode == 429 || // Too Many Requests
                statusCode == 423 || // Locked
                statusCode == 425 || // Too Early
                statusCode == 449 || // Retry With
                statusCode == 503;   // Service Unavailable
    }

