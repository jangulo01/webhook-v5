package com.sg.webhookservice.service.impl;

import com.sg.webhookservice.domain.entity.DeliveryAttempt;
import com.sg.webhookservice.domain.entity.Message;
import com.sg.webhookservice.domain.entity.WebhookConfig;
import com.sg.webhookservice.domain.repository.MessageRepository;
import com.sg.webhookservice.presentation.dto.BulkRetryRequestDto;
import com.sg.webhookservice.presentation.dto.BulkRetryResponseDto;
import com.sg.webhookservice.presentation.exception.ResourceNotFoundException;
import com.sg.webhookservice.presentation.exception.WebhookProcessingException;
import com.sg.webhookservice.presentation.exception.WebhookProcessingException1;
import com.sg.webhookservice.service.MessageService;
import com.sg.webhookservice.service.RetryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementación del servicio de gestión de reintentos de mensajes fallidos.
 * Maneja las estrategias de backoff y la programación de reintentos.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RetryServiceImpl implements RetryService {

    private final MessageRepository messageRepository;
    private final MessageService messageService;

    // Constantes de configuración
    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final String DEFAULT_BACKOFF_STRATEGY = "exponential";
    private static final int DEFAULT_INITIAL_INTERVAL = 60; // 60 segundos
    private static final double DEFAULT_BACKOFF_FACTOR = 2.0;
    private static final int DEFAULT_MAX_INTERVAL = 3600; // 1 hora
    private static final int DEFAULT_BATCH_SIZE = 50;

    @Override
    public OffsetDateTime calculateNextRetry(WebhookConfig config, int retryCount) {
        // Calcular intervalo de reintento según la estrategia configurada
        int delaySeconds = calculateBackoffDelay(
                config.getBackoffStrategy(),
                config.getInitialInterval(),
                config.getBackoffFactor(),
                config.getMaxInterval(),
                retryCount);

        // Calcular fecha y hora del próximo reintento
        return OffsetDateTime.now().plusSeconds(delaySeconds);
    }

    @Override
    public OffsetDateTime calculateNextRetryForMessage(Message message) {
        // Verificar que el mensaje pueda reintentarse
        if (!shouldRetryMessage(message)) {
            log.debug("Message {} should not be retried", message.getId());
            return null;
        }

        WebhookConfig config = message.getWebhookConfig();
        int retryCount = message.getRetryCount();

        // Si la configuración del webhook no está disponible, usar valores predeterminados
        if (config == null) {
            log.warn("WebhookConfig not available for message {}, using default retry values", message.getId());
            int delaySeconds = calculateDefaultBackoffDelay(retryCount);
            return OffsetDateTime.now().plusSeconds(delaySeconds);
        }

        // Calcular próximo reintento basado en la configuración del webhook
        return calculateNextRetry(config, retryCount);
    }

    @Override
    @Transactional
    @Scheduled(fixedDelayString = "${webhook.retry.scheduler.interval:60000}") // Por defecto cada 60 segundos
    public int processScheduledRetries() {
        int processedCount = 0;

        try {
            log.debug("Starting scheduled retry processing");

            // Buscar mensajes listos para reintento
            List<Message> retryMessages = messageRepository.findMessagesForRetry(OffsetDateTime.now());

            // Si no hay mensajes para reintentar, terminar
            if (retryMessages.isEmpty()) {
                log.debug("No messages found for retry");
                return 0;
            }

            log.info("Found {} messages ready for retry", retryMessages.size());

            // Procesar cada mensaje en lotes para evitar sobrecarga
            List<List<Message>> batches = partitionList(retryMessages, DEFAULT_BATCH_SIZE);

            for (List<Message> batch : batches) {
                for (Message message : batch) {
                    try {
                        // Invocar el servicio de mensajes para procesar el reintento
                        messageService.processRetry(message.getId());
                        processedCount++;
                    } catch (Exception e) {
                        log.error("Error processing retry for message {}: {}", message.getId(), e.getMessage(), e);
                    }
                }
            }

            log.info("Processed {} retry messages", processedCount);

        } catch (Exception e) {
            log.error("Error in scheduled retry processing: {}", e.getMessage(), e);
        }

        return processedCount;
    }

    @Override
    @Transactional
    public BulkRetryResponseDto processBulkRetry(BulkRetryRequestDto request) {
        // Delegamos al servicio de mensajes que ya implementa esta funcionalidad
        return messageService.bulkRetry(request);
    }

    @Override
    public boolean shouldRetryMessage(Message message) {
        // Verificar que el mensaje esté en estado fallido
        if (message.getStatus() != Message.MessageStatus.FAILED) {
            return false;
        }

        // Verificar que no haya alcanzado el máximo de reintentos
        if (message.hasExceededMaxRetries()) {
            return false;
        }

        // Obtener el último intento para verificar si es reintentable
        DeliveryAttempt lastAttempt = message.getLastAttempt();
        if (lastAttempt != null && !lastAttempt.shouldRetry()) {
            return false;
        }

        // Verificar que el mensaje no haya expirado
        WebhookConfig config = message.getWebhookConfig();
        if (config != null && config.isMessageExpired(message.getCreatedAt())) {
            return false;
        }

        return true;
    }

    @Override
    @Transactional
    public Message updateMessageForRetry(UUID messageId) {
        // Buscar el mensaje
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found", "message", messageId.toString()));

        // Verificar que el mensaje pueda reintentarse
        if (!shouldRetryMessage(message)) {
            throw new WebhookProcessingException1(
                    "Message cannot be retried",
                    WebhookProcessingException1.ProcessingPhase.RETRY,
                    message.getWebhookConfig() != null ? message.getWebhookConfig().getName() : null,
                    messageId
            );
        }

        // Calcular próximo reintento
        OffsetDateTime nextRetry = calculateNextRetryForMessage(message);

        // Actualizar mensaje con el próximo tiempo de reintento
        message.markAsFailed(message.getLastError(), nextRetry);
        return messageRepository.save(message);
    }

    @Override
    @Transactional(readOnly = true)
    public long getMessagesAwaitingRetryCount() {
        // Contar mensajes en estado fallido con próximo reintento programado
        return messageRepository.countByStatusAndNextRetryBefore(
                Message.MessageStatus.valueOf(Message.MessageStatus.FAILED.toString()), OffsetDateTime.now().plusDays(1));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Message> getExpiredMessages(int maxAgeHours, int limit) {
        // Calcular fecha límite para mensajes expirados
        OffsetDateTime cutoffTime = OffsetDateTime.now().minusHours(maxAgeHours);

        // Buscar mensajes expirados
        Page<Message> expiredMessages = messageRepository.findExpiredMessages(
                cutoffTime, PageRequest.of(0, limit));

        return expiredMessages.getContent();
    }

    @Override
    public Duration calculateEstimatedRetryPeriod(WebhookConfig config, int maxRetries) {
        long totalSeconds = 0;

        // Si la configuración es nula, usar valores predeterminados
        if (config == null) {
            for (int i = 0; i < maxRetries; i++) {
                totalSeconds += calculateDefaultBackoffDelay(i);
            }
        } else {
            // Calcular la duración total sumando todos los intervalos
            for (int i = 0; i < maxRetries; i++) {
                totalSeconds += calculateBackoffDelay(
                        config.getBackoffStrategy(),
                        config.getInitialInterval(),
                        config.getBackoffFactor(),
                        config.getMaxInterval(),
                        i);
            }
        }

        return Duration.ofSeconds(totalSeconds);
    }

    /**
     * Calcula el tiempo de espera para un reintento específico
     * según la estrategia de backoff.
     *
     * @param strategy Estrategia de backoff
     * @param initialInterval Intervalo inicial en segundos
     * @param backoffFactor Factor de multiplicación para backoff exponencial
     * @param maxInterval Intervalo máximo en segundos
     * @param retryCount Número de reintento actual
     * @return Tiempo de espera en segundos
     */
    private int calculateBackoffDelay(String strategy, int initialInterval, double backoffFactor,
                                      int maxInterval, int retryCount) {

        // Validar y aplicar valores predeterminados si es necesario
        strategy = (strategy != null && !strategy.isEmpty()) ? strategy.toLowerCase() : DEFAULT_BACKOFF_STRATEGY;
        initialInterval = (initialInterval > 0) ? initialInterval : DEFAULT_INITIAL_INTERVAL;
        backoffFactor = (backoffFactor > 0) ? backoffFactor : DEFAULT_BACKOFF_FACTOR;
        maxInterval = (maxInterval > 0) ? maxInterval : DEFAULT_MAX_INTERVAL;

        // Calcular retraso según la estrategia
        switch (strategy) {
            case "linear":
                return Math.min(initialInterval * (1 + retryCount), maxInterval);

            case "exponential":
                return (int) Math.min(initialInterval * Math.pow(backoffFactor, retryCount), maxInterval);

            case "fixed":
                return initialInterval;

            default:
                // Si no reconocemos la estrategia, usar exponencial por defecto
                log.warn("Unknown backoff strategy: {}, using exponential", strategy);
                return (int) Math.min(initialInterval * Math.pow(DEFAULT_BACKOFF_FACTOR, retryCount), maxInterval);
        }
    }

    /**
     * Calcula el tiempo de espera para un reintento usando valores predeterminados.
     *
     * @param retryCount Número de reintento
     * @return Tiempo de espera en segundos
     */
    private int calculateDefaultBackoffDelay(int retryCount) {
        return (int) Math.min(
                DEFAULT_INITIAL_INTERVAL * Math.pow(DEFAULT_BACKOFF_FACTOR, retryCount),
                DEFAULT_MAX_INTERVAL);
    }

    /**
     * Divide una lista en lotes de tamaño específico.
     *
     * @param list Lista a dividir
     * @param batchSize Tamaño del lote
     * @return Lista de lotes
     */
    private <T> List<List<T>> partitionList(List<T> list, int batchSize) {
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }

        List<List<T>> batches = new ArrayList<>();

        for (int i = 0; i < list.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, list.size());
            batches.add(list.subList(i, endIndex));
        }

        return batches;
    }
}