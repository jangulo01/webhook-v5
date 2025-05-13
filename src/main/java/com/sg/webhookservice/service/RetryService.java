package com.sg.webhookservice.service;

import com.yourcompany.webhookservice.model.Message;
import com.yourcompany.webhookservice.model.WebhookConfig;
import com.yourcompany.webhookservice.repository.MessageRepository;
import com.yourcompany.webhookservice.repository.WebhookConfigRepository;
import com.yourcompany.webhookservice.dto.BulkRetryRequest;
import com.yourcompany.webhookservice.exception.ResourceNotFoundException;
import com.yourcompany.webhookservice.kafka.producer.WebhookMessageProducer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service responsible for managing the retry logic for failed webhook messages.
 * Implements different backoff strategies and schedules retries based on configurations.
 */
@Service
public class RetryService {

    private static final Logger logger = LoggerFactory.getLogger(RetryService.class);
    
    private final MessageRepository messageRepository;
    private final WebhookConfigRepository webhookConfigRepository;
    private final WebhookMessageProducer messageProducer;
    private final MessageSenderService messageSenderService;
    
    @Value("${webhook.directMode:false}")
    private boolean directMode;
    
    @Value("${webhook.retry.kafka.topic:webhook-retries}")
    private String retryTopic;
    
    @Autowired
    public RetryService(
            MessageRepository messageRepository,
            WebhookConfigRepository webhookConfigRepository,
            WebhookMessageProducer messageProducer,
            MessageSenderService messageSenderService) {
        this.messageRepository = messageRepository;
        this.webhookConfigRepository = webhookConfigRepository;
        this.messageProducer = messageProducer;
        this.messageSenderService = messageSenderService;
    }
    
    /**
     * Calculates the next retry time based on the webhook configuration and current retry count.
     * 
     * @param config The webhook configuration containing retry parameters
     * @param retryCount Current retry count
     * @return LocalDateTime when the next retry should occur
     */
    public LocalDateTime calculateNextRetry(WebhookConfig config, int retryCount) {
        int initialInterval = config.getInitialInterval();
        String backoffStrategy = config.getBackoffStrategy();
        double backoffFactor = config.getBackoffFactor();
        int maxInterval = config.getMaxInterval();
        
        int delayInSeconds;
        
        switch (backoffStrategy.toLowerCase()) {
            case "linear":
                delayInSeconds = Math.min(initialInterval * (1 + retryCount), maxInterval);
                break;
            case "exponential":
                delayInSeconds = (int) Math.min(initialInterval * Math.pow(backoffFactor, retryCount), maxInterval);
                break;
            default:
                // Default to linear if strategy is unrecognized
                delayInSeconds = Math.min(initialInterval * (1 + retryCount), maxInterval);
                break;
        }
        
        return LocalDateTime.now().plusSeconds(delayInSeconds);
    }
    
    /**
     * Scheduled job that looks for failed messages that are ready for retry
     * and either processes them directly or sends them to Kafka.
     */
    @Scheduled(fixedDelayString = "${webhook.retry.scheduler.interval:30000}")
    @Transactional
    public void processScheduledRetries() {
        logger.info("Checking for messages ready for retry");
        LocalDateTime now = LocalDateTime.now();
        
        List<Message> messagesToRetry = messageRepository.findMessagesForRetry(now);
        logger.info("Found {} messages ready for retry", messagesToRetry.size());
        
        if (messagesToRetry.isEmpty()) {
            return;
        }
        
        if (directMode) {
            // In direct mode, process messages immediately
            for (Message message : messagesToRetry) {
                logger.info("Direct mode: Processing retry for message {}", message.getId());
                messageSenderService.processMessageAsync(message.getId());
            }
        } else {
            // In Kafka mode, send to retry topic
            for (Message message : messagesToRetry) {
                logger.info("Publishing message {} to Kafka retry topic", message.getId());
                messageProducer.sendToRetryTopic(message.getId());
            }
        }
    }
    
    /**
     * Process bulk retry request through admin endpoint.
     * Allows manual retry of failed messages within a specified time window.
     * 
     * @param request The bulk retry request containing parameters
     * @return Map with statistics about the retry operation
     */
    @Transactional
    public Map<String, Object> processBulkRetry(BulkRetryRequest request) {
        int hours = request.getHours() != null ? request.getHours() : 24;
        int limit = request.getLimit() != null ? request.getLimit() : 100;
        String destinationUrl = request.getDestinationUrl();
        
        LocalDateTime cutoffTime = LocalDateTime.now().minusHours(hours);
        List<Message> failedMessages = messageRepository.findFailedMessagesUpdatedAfter(
                cutoffTime, 
                PageRequest.of(0, limit)
        );
        
        Map<String, Object> results = new HashMap<>();
        results.put("total", failedMessages.size());
        results.put("successful", 0);
        results.put("failed", 0);
        
        List<Map<String, Object>> messageResults = failedMessages.stream()
                .map(message -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("message_id", message.getId().toString());
                    result.put("webhook_name", message.getWebhookConfig().getName());
                    
                    boolean success = messageSenderService.sendMessageWithCustomDestination(
                            message.getId(), 
                            destinationUrl, 
                            null
                    );
                    
                    if (success) {
                        results.put("successful", (Integer) results.get("successful") + 1);
                        result.put("status", "delivered");
                    } else {
                        results.put("failed", (Integer) results.get("failed") + 1);
                        result.put("status", "failed");
                    }
                    
                    return result;
                })
                .collect(Collectors.toList());
        
        results.put("messages", messageResults);
        return results;
    }
    
    /**
     * Check if a message should be retried based on its current state and configuration.
     * 
     * @param message The message to evaluate
     * @return true if the message should be retried, false otherwise
     */
    public boolean shouldRetryMessage(Message message) {
        WebhookConfig config = message.getWebhookConfig();
        if (config == null) {
            return false;
        }
        
        return message.getRetryCount() < config.getMaxRetries();
    }
    
    /**
     * Update message next retry time based on backoff strategy.
     * 
     * @param messageId The ID of the message to update
     * @return The updated message with new retry information
     */
    @Transactional
    public Message updateMessageForRetry(UUID messageId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found: " + messageId));
        
        WebhookConfig config = message.getWebhookConfig();
        int newRetryCount = message.getRetryCount() + 1;
        
        if (newRetryCount <= config.getMaxRetries()) {
            LocalDateTime nextRetry = calculateNextRetry(config, newRetryCount);
            message.setRetryCount(newRetryCount);
            message.setNextRetry(nextRetry);
            message.setStatus("failed");
            
            logger.info("Message {} scheduled for retry #{} at {}", 
                    messageId, newRetryCount, nextRetry);
            
            return messageRepository.save(message);
        } else {
            // Max retries reached, mark as permanently failed
            message.setStatus("failed");
            message.setNextRetry(null);
            logger.info("Message {} has failed permanently after {} attempts", 
                    messageId, newRetryCount);
            
            return messageRepository.save(message);
        }
    }
    
    /**
     * Check total count of messages pending retry.
     * 
     * @return Count of messages that are waiting to be retried
     */
    public long getMessagesAwaitingRetryCount() {
        return messageRepository.countByStatusAndNextRetryBefore("failed", LocalDateTime.now());
    }
    
    /**
     * Get messages that have been in failed state for more than the configured max age.
     * 
     * @param maxAgeHours Maximum age in hours
     * @param limit Maximum number of results to return
     * @return List of expired messages
     */
    public List<Message> getExpiredMessages(int maxAgeHours, int limit) {
        LocalDateTime cutoffTime = LocalDateTime.now().minusHours(maxAgeHours);
        return messageRepository.findExpiredMessages(cutoffTime, PageRequest.of(0, limit));
    }
    
    /**
     * Calculate estimated retry time based on backoff strategy parameters.
     * Useful for estimating total delivery time with retries.
     * 
     * @param config Webhook configuration with retry parameters
     * @param maxRetries Number of retries to calculate
     * @return Duration of the total retry period
     */
    public Duration calculateEstimatedRetryPeriod(WebhookConfig config, int maxRetries) {
        long totalSeconds = 0;
        
        for (int retryCount = 0; retryCount < maxRetries; retryCount++) {
            int initialInterval = config.getInitialInterval();
            String backoffStrategy = config.getBackoffStrategy();
            double backoffFactor = config.getBackoffFactor();
            int maxInterval = config.getMaxInterval();
            
            long delayInSeconds;
            
            if ("linear".equalsIgnoreCase(backoffStrategy)) {
                delayInSeconds = Math.min(initialInterval * (1 + retryCount), maxInterval);
            } else if ("exponential".equalsIgnoreCase(backoffStrategy)) {
                delayInSeconds = (long) Math.min(initialInterval * Math.pow(backoffFactor, retryCount), maxInterval);
            } else {
                delayInSeconds = Math.min(initialInterval * (1 + retryCount), maxInterval);
            }
            
            totalSeconds += delayInSeconds;
        }
        
        return Duration.ofSeconds(totalSeconds);
    }
}