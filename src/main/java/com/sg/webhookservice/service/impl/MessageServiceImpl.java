package com.sg.webhookservice.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sg.webhookservice.domain.entity.DeliveryAttempt;
import com.sg.webhookservice.domain.entity.Message;
import com.sg.webhookservice.domain.entity.WebhookConfig;
import com.sg.webhookservice.domain.repository.MessageRepository;
import com.sg.webhookservice.domain.repository.WebhookConfigRepository;
import com.sg.webhookservice.presentation.dto.*;
import com.sg.webhookservice.presentation.exception.ResourceNotFoundException;
import com.sg.webhookservice.presentation.exception.WebhookProcessingException;
import com.sg.webhookservice.presentation.exception.WebhookProcessingException1;
import com.sg.webhookservice.presentation.mapper.MessageMapper;
import com.sg.webhookservice.service.HealthMonitoringService;
import com.sg.webhookservice.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementación del servicio de gestión de mensajes.
 * Define operaciones para enviar, procesar y consultar mensajes de webhook.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

    private final MessageRepository messageRepository;
    private final WebhookConfigRepository webhookConfigRepository;
    private final MessageMapper messageMapper;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final HealthMonitoringService healthMonitoringService;

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final int MAX_RESPONSE_LENGTH = 500;
    private static final String SIGNATURE_HEADER = "X-Webhook-Signature";

    @Override
    @Transactional
    public MessageResponseDto receiveWebhook(String webhookName, WebhookRequestDto requestDto) {
        log.info("Received webhook for '{}': {}", webhookName, requestDto);

        // Buscar configuración del webhook
        WebhookConfig config = webhookConfigRepository.findByNameAndActiveTrue(webhookName)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Active webhook configuration not found", "webhook", webhookName));

        // Validar que el webhook esté activo
        if (!config.isActive()) {
            throw new WebhookProcessingException(
                    "Webhook is disabled",
                    WebhookProcessingException.ProcessingPhase.VALIDATION,
                    webhookName,
                    null
            );
        }

        try {
            // Convertir payload a JSON
            String payload = objectMapper.writeValueAsString(requestDto.getPayload());

            // Crear nuevo mensaje
            Message message = Message.builder()
                    .webhookConfig(config)
                    .payload(payload)
                    .targetUrl(requestDto.getTargetUrl() != null ? requestDto.getTargetUrl() : config.getTargetUrl())
                    .status(Message.MessageStatus.PENDING)
                    .retryCount(0)
                    .headers(requestDto.getHeaders() != null ? objectMapper.writeValueAsString(requestDto.getHeaders()) : config.getHeaders())
                    .build();

            // Calcular firma HMAC si se requiere
            message.setSignature(calculateSignature(config.getSecret(), payload));

            // Guardar mensaje
            Message savedMessage = messageRepository.save(message);

            // Si se solicita entrega inmediata, procesar asíncronamente
            if (requestDto.isDeliverImmediately()) {
                processMessageAsync(savedMessage.getId());
            }

            // Construir respuesta
            MessageResponseDto response = new MessageResponseDto();
            response.setMessageId(String.valueOf(savedMessage.getId()));
            response.setStatus(savedMessage.getStatus().toString());
            response.setTimestamp(savedMessage.getCreatedAt());

            return response;

        } catch (JsonProcessingException e) {
            log.error("Error processing webhook payload: {}", e.getMessage(), e);
            throw new WebhookProcessingException(
                    "Error processing webhook payload: " + e.getMessage(),
                    WebhookProcessingException.ProcessingPhase.SERIALIZATION,
                    webhookName,
                    null
            );
        }
    }

    @Override
    @Transactional(readOnly = true)
    public MessageDto getMessageStatus(UUID messageId) {
        Message message = messageRepository.findByIdWithDeliveryAttempts(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found", "message", messageId.toString()));

        return messageMapper.toDto(message);
    }

    @Override
    @Transactional
    public void processMessage(UUID messageId) {
        // Obtener mensaje con sus intentos de entrega
        Message message = messageRepository.findByIdWithDeliveryAttempts(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found", "message", messageId.toString()));

        // Verificar que el mensaje esté en estado pendiente
        if (message.getStatus() != Message.MessageStatus.PENDING) {
            throw new WebhookProcessingException1(
                    "Message is not in PENDING state",
                    WebhookProcessingException1.ProcessingPhase.VALIDATION,
                    message.getWebhookConfig().getName(),
                    messageId
            );
        }

        // Marcar como en procesamiento
        message.markAsProcessing();
        messageRepository.save(message);

        // Realizar envío HTTP
        deliverWebhook(message);
    }

    @Override
    @Transactional
    public void processRetry(UUID messageId) {
        // Obtener mensaje con sus intentos de entrega
        Message message = messageRepository.findByIdWithDeliveryAttempts(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found", "message", messageId.toString()));

        // Verificar que el mensaje esté en estado fallido y pueda reintentarse
        if (message.getStatus() != Message.MessageStatus.FAILED || !message.isRetryable()) {
            throw new WebhookProcessingException1(
                    "Message is not in a retryable state",
                    WebhookProcessingException1.ProcessingPhase.VALIDATION,
                    message.getWebhookConfig().getName(),
                    messageId
            );
        }

        // Verificar que no se exceda el máximo de reintentos
        if (message.hasExceededMaxRetries()) {
            log.warn("Message {} has reached maximum retry attempts", messageId);
            return;
        }

        // Marcar como en procesamiento
        message.markAsProcessing();
        messageRepository.save(message);

        // Realizar envío HTTP
        deliverWebhook(message);
    }

    @Override
    @Transactional
    public void cancelMessage(UUID messageId) {
        // Obtener mensaje
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found", "message", messageId.toString()));

        // Cancelar mensaje solo si no está en estado terminal
        if (!message.isInTerminalState()) {
            message.cancel();
            messageRepository.save(message);
            log.info("Message {} cancelled", messageId);
        } else {
            log.warn("Attempted to cancel message {} that is already in terminal state: {}",
                    messageId, message.getStatus());
        }
    }

    @Transactional
    @Override
    public BulkRetryResponseDto bulkRetry(BulkRetryRequestDto request) {
        BulkRetryResponseDto response = new BulkRetryResponseDto();
        int processedCount = 0;
        int successCount = 0;
        List<UUID> failedIds = new ArrayList<>();

        log.info("Starting bulk retry operation for {} messages",
                request.getMessageIds() != null ? request.getMessageIds().size() : "all failed");

        // Determinar mensajes a reintentar
        List<Message> messagesToRetry;

        if (request.getMessageIds() != null && !request.getMessageIds().isEmpty()) {
            // Reintentar mensajes específicos
            messagesToRetry = request.getMessageIds().stream()
                    .map(id -> messageRepository.findById(id)
                            .orElse(null))
                    .filter(Objects::nonNull)
                    .filter(m -> m.getStatus() == Message.MessageStatus.FAILED)
                    .collect(Collectors.toList());
        } else {
            // Reintentar todos los mensajes fallidos recientes
            OffsetDateTime cutoffTime = OffsetDateTime.now().minusHours(
                    request.getTimeRangeHours() != null ? request.getTimeRangeHours() : 24);

            Page<Message> failedMessages = messageRepository.findFailedMessagesUpdatedAfter(
                    cutoffTime, Pageable.unpaged());
            messagesToRetry = failedMessages.getContent();
        }

        // Procesar cada mensaje
        for (Message message : messagesToRetry) {
            try {
                processedCount++;

                // Marcar como en procesamiento
                message.markAsProcessing();
                messageRepository.save(message);

                // Entregar webhook
                DeliveryAttempt attempt = deliverWebhook(message);

                if (attempt.isSuccessful()) {
                    successCount++;
                } else {
                    failedIds.add(message.getId());
                }
            } catch (Exception e) {
                log.error("Error retrying message {}: {}", message.getId(), e.getMessage(), e);
                failedIds.add(message.getId());
            }
        }

        // Construir respuesta
        response.setProcessedCount(processedCount);
        response.setSuccessCount(successCount);
        response.setFailedCount(failedIds.size());
        response.setFailedIds(failedIds);

        log.info("Bulk retry completed: {}/{} successful", successCount, processedCount);

        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MessageDto> searchMessages(
            String webhookName,
            String status,
            OffsetDateTime fromDate,
            OffsetDateTime toDate,
            Pageable pageable) {

        // Convertir string de status a enum si está presente
        Message.MessageStatus statusEnum = null;
        if (status != null && !status.isEmpty()) {
            try {
                statusEnum = Message.MessageStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid status filter: {}", status);
                // Continuar con statusEnum null, que ignorará este filtro
            }
        }

        // Realizar búsqueda
        Page<Message> messages = messageRepository.searchMessages(
                webhookName, statusEnum, fromDate, toDate, pageable);

        // Convertir resultados a DTOs
        return messages.map(messageMapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getMessageStatistics(String webhookName, Integer timeRange) {
        // Usar tiempo por defecto de 24 horas si no se especifica
        int hours = timeRange != null ? timeRange : 24;
        OffsetDateTime cutoffTime = OffsetDateTime.now().minusHours(hours);

        Map<String, Object> statistics = new HashMap<>();

        try {
            // TODO: Implementar estadísticas detalladas por webhook, status, etc.
            // Esta implementación es simplificada

            // Agregar información básica
            statistics.put("timeRangeHours", hours);
            statistics.put("fromDate", cutoffTime);
            statistics.put("toDate", OffsetDateTime.now());

            if (webhookName != null && !webhookName.isEmpty()) {
                // Estadísticas para un webhook específico
                WebhookConfig config = webhookConfigRepository.findByName(webhookName)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Webhook configuration not found", "webhook", webhookName));

                statistics.put("webhookId", config.getId());
                statistics.put("webhookName", webhookName);

                // TODO: Obtener estadísticas específicas del webhook
            }

            // TODO: Implementar conteo por estado, tasa de éxito, etc.
            statistics.put("pendingCount", 0);
            statistics.put("deliveredCount", 0);
            statistics.put("failedCount", 0);
            statistics.put("avgResponseTime", 0);

        } catch (Exception e) {
            log.error("Error calculating message statistics: {}", e.getMessage(), e);
            statistics.put("error", "Error calculating statistics: " + e.getMessage());
        }

        return statistics;
    }

    @Override
    @Transactional
    public int processPendingMessages() {
        int processedCount = 0;

        // Buscar mensajes pendientes
        List<Message> pendingMessages = messageRepository.findByStatus(Message.MessageStatus.PENDING);
        log.info("Found {} pending messages to process", pendingMessages.size());

        // Procesar cada mensaje
        for (Message message : pendingMessages) {
            try {
                // Marcar como en procesamiento
                message.markAsProcessing();
                messageRepository.save(message);

                // Entregar webhook
                deliverWebhook(message);

                processedCount++;
            } catch (Exception e) {
                log.error("Error processing pending message {}: {}", message.getId(), e.getMessage(), e);
            }
        }

        // Buscar mensajes listos para reintento
        List<Message> retryMessages = messageRepository.findMessagesForRetry(OffsetDateTime.now());
        log.info("Found {} messages ready for retry", retryMessages.size());

        // Procesar cada mensaje para reintento
        for (Message message : retryMessages) {
            try {
                // Marcar como en procesamiento
                message.markAsProcessing();
                messageRepository.save(message);

                // Entregar webhook
                deliverWebhook(message);

                processedCount++;
            } catch (Exception e) {
                log.error("Error processing retry message {}: {}", message.getId(), e.getMessage(), e);
            }
        }

        log.info("Processed {} pending/retry messages", processedCount);
        return processedCount;
    }

    /**
     * Versión asíncrona de processMessage para entregas inmediatas.
     */
    @Async
    public void processMessageAsync(UUID messageId) {
        try {
            processMessage(messageId);
        } catch (Exception e) {
            log.error("Error processing message asynchronously {}: {}", messageId, e.getMessage(), e);
        }
    }

    /**
     * Entrega un webhook a su destino.
     *
     * @param message Mensaje a entregar
     * @return Intento de entrega registrado
     */
    private DeliveryAttempt deliverWebhook(Message message) {
        log.debug("Delivering webhook message {} to {}", message.getId(), message.getTargetUrl());
        UUID webhookId = message.getWebhookConfig().getId();

        long startTime = System.currentTimeMillis();
        DeliveryAttempt attempt = DeliveryAttempt.builder()
                .message(message)
                .attemptNumber(message.getRetryCount() + 1)
                .build();

        try {
            // Preparar headers
            HttpHeaders headers = new HttpHeaders();

            // Agregar firma si existe
            if (message.getSignature() != null) {
                headers.set(SIGNATURE_HEADER, message.getSignature());
            }

            // Agregar headers personalizados si existen
            if (message.getHeaders() != null && !message.getHeaders().isEmpty()) {
                Map<String, String> customHeaders = objectMapper.readValue(message.getHeaders(), Map.class);
                customHeaders.forEach(headers::set);
            }

            // Preparar payload
            Object payload = objectMapper.readValue(message.getPayload(), Object.class);

            // Crear entidad HTTP
            HttpEntity<Object> requestEntity = new HttpEntity<>(payload, headers);

            // Realizar solicitud HTTP
            ResponseEntity<String> response = restTemplate.postForEntity(
                    message.getTargetUrl(), requestEntity, String.class);

            // Registrar tiempo de respuesta
            long requestDuration = System.currentTimeMillis() - startTime;
            attempt.setRequestDuration(requestDuration);

            // Registrar respuesta
            attempt.setStatusCode(response.getStatusCodeValue());
            attempt.setResponseBody(DeliveryAttempt.truncateResponse(
                    response.getBody(), MAX_RESPONSE_LENGTH));

            // Actualizar estado del mensaje
            if (attempt.isSuccessful()) {
                message.markAsDelivered();

                // Registrar entrega exitosa en estadísticas
                healthMonitoringService.recordSuccessfulDelivery(
                        webhookId, response.getStatusCodeValue(), requestDuration);

                log.info("Webhook message {} delivered successfully: HTTP {}",
                        message.getId(), response.getStatusCodeValue());
            } else {
                // Determinar si debe reintentarse
                OffsetDateTime nextRetry = calculateNextRetry(message, attempt);
                message.markAsFailed(
                        "HTTP error: " + response.getStatusCodeValue(), nextRetry);

                // Registrar fallo en estadísticas
                healthMonitoringService.recordFailedDelivery(
                        webhookId, "HTTP error: " + response.getStatusCodeValue());

                log.warn("Webhook message {} delivery failed: HTTP {}",
                        message.getId(), response.getStatusCodeValue());
            }

        } catch (HttpStatusCodeException e) {
            // Error HTTP con código de respuesta
            long requestDuration = System.currentTimeMillis() - startTime;
            attempt.setRequestDuration(requestDuration);
            attempt.setStatusCode(e.getRawStatusCode());
            attempt.setResponseBody(DeliveryAttempt.truncateResponse(
                    e.getResponseBodyAsString(), MAX_RESPONSE_LENGTH));
            attempt.setError(e.getMessage());

            // Determinar si debe reintentarse
            OffsetDateTime nextRetry = calculateNextRetry(message, attempt);
            message.markAsFailed(
                    "HTTP error: " + e.getRawStatusCode() + " - " + e.getMessage(), nextRetry);

            // Registrar fallo en estadísticas
            healthMonitoringService.recordFailedDelivery(
                    webhookId, "HTTP error: " + e.getRawStatusCode());

            log.warn("Webhook message {} delivery failed with HTTP error {}: {}",
                    message.getId(), e.getRawStatusCode(), e.getMessage());

        } catch (ResourceAccessException e) {
            // Error de conexión
            long requestDuration = System.currentTimeMillis() - startTime;
            attempt.setRequestDuration(requestDuration);
            attempt.setError(e.getMessage());

            // Determinar si debe reintentarse
            OffsetDateTime nextRetry = calculateNextRetry(message, attempt);
            message.markAsFailed("Connection error: " + e.getMessage(), nextRetry);

            // Registrar error de conexión en estadísticas
            healthMonitoringService.recordConnectionError(webhookId);

            log.warn("Webhook message {} delivery failed with connection error: {}",
                    message.getId(), e.getMessage());

        } catch (Exception e) {
            // Otros errores
            long requestDuration = System.currentTimeMillis() - startTime;
            attempt.setRequestDuration(requestDuration);
            attempt.setError(e.getMessage());

            // Determinar si debe reintentarse
            OffsetDateTime nextRetry = calculateNextRetry(message, attempt);
            message.markAsFailed("Error: " + e.getMessage(), nextRetry);

            // Registrar fallo en estadísticas
            healthMonitoringService.recordFailedDelivery(
                    webhookId, "Error: " + e.getMessage());

            log.error("Webhook message {} delivery failed with error: {}",
                    message.getId(), e.getMessage(), e);
        }

        // Agregar el intento al historial y guardar
        message.addDeliveryAttempt(attempt);
        messageRepository.save(message);

        return attempt;
    }

    /**
     * Calcula el momento para el próximo intento basado en la configuración y el historial.
     *
     * @param message Mensaje actual
     * @param attempt Último intento de entrega
     * @return Tiempo para el próximo intento o null si no debe reintentarse
     */
    private OffsetDateTime calculateNextRetry(Message message, DeliveryAttempt attempt) {
        // Si no debe reintentarse según las condiciones del último intento
        if (!attempt.shouldRetry()) {
            return null;
        }

        // Si ha excedido el máximo de reintentos
        if (message.hasExceededMaxRetries()) {
            return null;
        }

        // Obtener configuración de webhook
        WebhookConfig config = message.getWebhookConfig();

        // Si el mensaje ha expirado
        if (config.isMessageExpired(message.getCreatedAt())) {
            return null;
        }

        // Calcular intervalo de reintento basado en la estrategia y el factor
        int retryCount = message.getRetryCount();
        int delay = config.calculateBackoffDelay(retryCount);

        // Aplicar factor dinámico basado en tipo de error
        double factor = attempt.getRetryDelayFactor();
        delay = (int) (delay * factor);

        // Calcular próximo momento de reintento
        return OffsetDateTime.now().plusSeconds(delay);
    }

    /**
     * Calcula la firma HMAC SHA-256 para el mensaje.
     *
     * @param secret Secreto usado para la firma
     * @param payload Contenido a firmar
     * @return Firma como string hexadecimal
     */
    private String calculateSignature(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            mac.init(secretKeySpec);

            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));

            // Convertir a string hexadecimal
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            return hexString.toString();

        } catch (Exception e) {
            log.error("Error calculating signature: {}", e.getMessage(), e);
            return null;
        }
    }
}