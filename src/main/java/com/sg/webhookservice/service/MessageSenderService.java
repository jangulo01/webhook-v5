package com.sg.webhookservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sg.webhookservice.exception.ResourceNotFoundException;
import com.sg.webhookservice.exception.WebhookProcessingException;
import com.sg.webhookservice.model.DeliveryAttempt;
import com.sg.webhookservice.model.Message;
import com.sg.webhookservice.model.Message.MessageStatus;
import com.sg.webhookservice.model.WebhookConfig;
import com.sg.webhookservice.repository.DeliveryAttemptRepository;
import com.sg.webhookservice.repository.MessageRepository;
import com.sg.webhookservice.repository.WebhookConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Servicio responsable del envío de mensajes de webhook a sus destinos.
 *
 * Se encarga de:
 * - Envío asíncrono de mensajes de webhook
 * - Envío a destinos personalizados (override)
 * - Registro de resultados de envío
 * - Manejo de errores durante el envío
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MessageSenderService {

    private final MessageRepository messageRepository;
    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final WebhookConfigRepository webhookConfigRepository;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final HmacService hmacService;
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
     * Procesa un mensaje de webhook de forma asíncrona.
     * Este método es llamado por webhookService y retryService para iniciar
     * el procesamiento sin bloquear el hilo llamante.
     *
     * @param messageId ID del mensaje a procesar
     * @return Completable future con el resultado del procesamiento
     */
    @Async
    public CompletableFuture<Boolean> processMessageAsync(UUID messageId) {
        try {
            log.info("Iniciando procesamiento asíncrono del mensaje {}", messageId);
            processMessage(messageId);
            return CompletableFuture.completedFuture(true);
        } catch (Exception e) {
            log.error("Error en procesamiento asíncrono del mensaje {}: {}", messageId, e.getMessage(), e);
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * Procesa un mensaje obteniendo su información de la base de datos
     * y enviándolo a su destino.
     *
     * @param messageId ID del mensaje a procesar
     * @throws WebhookProcessingException Si ocurre un error durante el procesamiento
     * @throws ResourceNotFoundException Si el mensaje no existe
     */
    @Transactional
    public void processMessage(UUID messageId) {
        log.debug("Procesando mensaje: {}", messageId);

        // Obtener mensaje con bloqueo para actualización
        Message message = getMessage(messageId);

        try {
            // Intentar marcar como en procesamiento
            int updated = messageRepository.markAsProcessing(messageId);
            if (updated == 0) {
                log.warn("No se pudo marcar mensaje {} como en procesamiento, posible estado incorrecto", messageId);
                // Verificar estado actual para diagnóstico
                log.debug("Estado actual del mensaje {}: {}", messageId, message.getStatus());
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
            deliverMessage(message, null);

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
     * Envía un mensaje a un destino personalizado, ignorando la URL
     * configurada para el webhook.
     *
     * @param messageId ID del mensaje a enviar
     * @param customDestinationUrl URL de destino personalizada (override)
     * @param customSecret Secreto personalizado (opcional)
     * @return true si el envío fue exitoso, false en caso contrario
     */
    @Transactional
    public boolean sendMessageWithCustomDestination(UUID messageId, String customDestinationUrl, String customSecret) {
        log.info("Enviando mensaje {} a destino personalizado: {}",
                messageId, customDestinationUrl != null ? customDestinationUrl : "default");

        Message message = getMessage(messageId);

        try {
            // Marcar como en procesamiento
            int updated = messageRepository.markAsProcessing(messageId);
            if (updated == 0) {
                log.warn("No se pudo marcar mensaje {} para envío personalizado", messageId);
                return false;
            }

            // Regenerar firma si se proporciona un secreto personalizado
            if (customSecret != null && !customSecret.isEmpty()) {
                String newSignature = hmacService.generateSignature(message.getPayload(), customSecret, true);
                message.setSignature(newSignature);
                // No guardamos la nueva firma en DB ya que es solo para este envío
            }

            // Enviar mensaje con destino personalizado
            deliverMessage(message, customDestinationUrl);

            return message.getStatus() == MessageStatus.DELIVERED;

        } catch (Exception e) {
            log.error("Error enviando mensaje {} a destino personalizado: {}",
                    messageId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Entrega un mensaje a su destino.
     *
     * @param message Mensaje a entregar
     * @param customDestinationUrl URL de destino personalizada (puede ser null)
     * @throws WebhookProcessingException Si ocurre error en la entrega
     */
    private void deliverMessage(Message message, String customDestinationUrl) {
        UUID messageId = message.getId();
        WebhookConfig config = message.getWebhookConfig();
        String webhookName = config.getName();

        // Determinar URL de destino (personalizada o de la configuración)
        String targetUrl = customDestinationUrl != null ? customDestinationUrl : message.getTargetUrl();

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
            HttpEntity<String> entity = new HttpEntity<>(message.getPayload(), headers);

            ResponseEntity<String> response = restTemplate.postForEntity(targetUrl, entity, String.class);

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
                        config.getId(),
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
     * Esta es la solución al problema del tipo genérico: en lugar de llamar al método
     * de RetryService que usa tipos genéricos incorrectos, calculamos directamente
     * el tiempo de reintento.
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
            log.warn("Mensaje {} ha excedido el máximo de reintentos ({}), no se reintentará",
                    messageId, message.getRetryCount());

            messageRepository.markAsFailed(
                    messageId,
                    errorMessage + " - Máximo de reintentos excedido",
                    null
            );

            return;
        }

        // Calcular tiempo de próximo reintento sin usar el método problemático
        OffsetDateTime nextRetry = calculateNextRetryTime(message.getRetryCount(), config);

        // Registrar reintento
        messageRepository.markAsFailed(
                messageId,
                errorMessage,
                nextRetry
        );

        log.info("Mensaje {} programado para reintento en: {}", messageId, nextRetry);
    }

    /**
     * Calcula el tiempo para el próximo reintento basado en la estrategia de backoff configurada.
     * Esta implementación evita el uso del método problemático en RetryService.
     *
     * @param retryCount Número actual de reintentos
     * @param config Configuración del webhook
     * @return Tiempo para el próximo reintento
     */
    private OffsetDateTime calculateNextRetryTime(int retryCount, WebhookConfig config) {
        String strategy = config.getBackoffStrategy();
        int initialInterval = config.getInitialInterval();
        double factor = config.getBackoffFactor();
        int maxInterval = config.getMaxInterval();

        int delayInSeconds;

        if ("exponential".equalsIgnoreCase(strategy)) {
            // Backoff exponencial
            delayInSeconds = (int) Math.min(initialInterval * Math.pow(factor, retryCount), maxInterval);
        } else if ("linear".equalsIgnoreCase(strategy)) {
            // Backoff lineal
            delayInSeconds = Math.min(initialInterval * (1 + retryCount), maxInterval);
        } else {
            // Estrategia por defecto (fija)
            delayInSeconds = initialInterval;
        }

        return OffsetDateTime.now().plusSeconds(delayInSeconds);
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

    /**
     * Determina si un error es recuperable (amerita reintento).
     *
     * @param e Excepción a analizar
     * @return true si debería reintentarse, false si no
     */
    private boolean isRecoverableError(Exception e) {
        // Errores de red/conexión son generalmente recuperables
        return e instanceof java.net.ConnectException ||
                e instanceof java.net.UnknownHostException ||
                e instanceof java.net.SocketTimeoutException ||
                e instanceof java.io.IOException ||
                e instanceof org.springframework.web.client.ResourceAccessException;
    }

    /**
     * Trunca un payload para log si es muy largo.
     *
     * @param payload Payload completo
     * @return Payload truncado si necesario
     */
    private String truncateForLog(String payload) {
        if (payload == null) {
            return "null";
        }

        if (payload.length() <= maxPayloadLogLength) {
            return payload;
        }

        return payload.substring(0, maxPayloadLogLength) + "... [truncado]";
    }

    /**
     * Trunca respuesta si es muy larga.
     *
     * @param response Cuerpo de respuesta
     * @return Respuesta truncada si necesario
     */
    private String truncateResponse(String response) {
        return DeliveryAttempt.truncateResponse(response, maxPayloadLogLength);
    }

    /**
     * Convierte headers HTTP a JSON para almacenamiento.
     *
     * @param headers Headers HTTP
     * @return Representación JSON
     */
    private String convertHeadersToJson(HttpHeaders headers) {
        try {
            Map<String, String> headerMap = new HashMap<>();

            // Convertir MultiValueMap a Map simple con valores concatenados
            headers.forEach((name, values) -> {
                if (!values.isEmpty()) {
                    headerMap.put(name, String.join(", ", values));
                }
            });

            return objectMapper.writeValueAsString(headerMap);
        } catch (Exception e) {
            log.warn("Error convirtiendo headers a JSON: {}", e.getMessage());
            return "{}";
        }
    }

    /**
     * Obtiene un mensaje por ID.
     *
     * @param messageId ID del mensaje
     * @return Mensaje encontrado
     * @throws ResourceNotFoundException Si no existe
     */
    private Message getMessage(UUID messageId) {
        return messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Mensaje no encontrado: " + messageId,
                        "message",
                        messageId.toString()
                ));
    }
}