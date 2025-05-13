package com.sg.webhookservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sg.webhookservice.dto.WebhookConfigDto;
import com.sg.webhookservice.dto.WebhookRequestDto;
import com.sg.webhookservice.exception.ResourceAlreadyExistsException;
import com.sg.webhookservice.exception.ResourceNotFoundException;
import com.sg.webhookservice.exception.WebhookProcessingException;
import com.sg.webhookservice.kafka.producer.WebhookMessageProducer;
import com.sg.webhookservice.model.Message;
import com.sg.webhookservice.model.WebhookConfig;
import com.sg.webhookservice.model.WebhookHealthStats;
import com.sg.webhookservice.repository.MessageRepository;
import com.sg.webhookservice.repository.WebhookConfigRepository;
import com.sg.webhookservice.repository.WebhookHealthStatsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service responsible for webhook management operations including
 * receiving, configuring, and processing webhook events.
 */
@Service
public class WebhookService {
    
    private static final Logger logger = LoggerFactory.getLogger(WebhookService.class);
    
    private final WebhookConfigRepository webhookConfigRepository;
    private final MessageRepository messageRepository;
    private final WebhookHealthStatsRepository healthStatsRepository;
    private final WebhookMessageProducer messageProducer;
    private final HmacService hmacService;
    private final MessageSenderService messageSenderService;
    private final ObjectMapper objectMapper;
    
    @Value("${webhook.directMode:false}")
    private boolean directMode;
    
    @Value("${webhook.defaultSecret:test-secret}")
    private String defaultSecret;
    
    @Autowired
    public WebhookService(
            WebhookConfigRepository webhookConfigRepository,
            MessageRepository messageRepository,
            WebhookHealthStatsRepository healthStatsRepository,
            WebhookMessageProducer messageProducer,
            HmacService hmacService,
            MessageSenderService messageSenderService,
            ObjectMapper objectMapper) {
        this.webhookConfigRepository = webhookConfigRepository;
        this.messageRepository = messageRepository;
        this.healthStatsRepository = healthStatsRepository;
        this.messageProducer = messageProducer;
        this.hmacService = hmacService;
        this.messageSenderService = messageSenderService;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Get all webhook configurations
     * 
     * @return List of all active webhook configurations
     */
    public List<WebhookConfig> getAllWebhookConfigs() {
        return webhookConfigRepository.findByActiveTrue();
    }
    
    /**
     * Find a webhook configuration by name
     * 
     * @param name Name of the webhook configuration
     * @return The webhook configuration
     * @throws ResourceNotFoundException if webhook not found
     */
    public WebhookConfig getWebhookConfigByName(String name) {
        return webhookConfigRepository.findByNameAndActiveTrue(name)
                .orElseThrow(() -> new ResourceNotFoundException("Webhook configuration not found: " + name));
    }
    
    /**
     * Find a webhook configuration by ID
     * 
     * @param id ID of the webhook configuration
     * @return The webhook configuration
     * @throws ResourceNotFoundException if webhook not found
     */
    public WebhookConfig getWebhookConfigById(UUID id) {
        return webhookConfigRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Webhook configuration not found: " + id));
    }
    
    /**
     * Create a new webhook configuration
     * 
     * @param configDto The webhook configuration data
     * @return The created webhook configuration
     * @throws ResourceAlreadyExistsException if a webhook with the name already exists
     */
    @Transactional
    public WebhookConfig createWebhookConfig(WebhookConfigDto configDto) {
        // Check if webhook with the same name already exists
        if (webhookConfigRepository.findByName(configDto.getName()).isPresent()) {
            throw new ResourceAlreadyExistsException("Webhook with name '" + configDto.getName() + "' already exists");
        }
        
        WebhookConfig config = new WebhookConfig();
        config.setName(configDto.getName());
        config.setTargetUrl(configDto.getTargetUrl());
        config.setSecret(configDto.getSecret());
        config.setMaxRetries(configDto.getMaxRetries() != null ? configDto.getMaxRetries() : 3);
        config.setBackoffStrategy(configDto.getBackoffStrategy() != null ? configDto.getBackoffStrategy() : "exponential");
        config.setInitialInterval(configDto.getInitialInterval() != null ? configDto.getInitialInterval() : 60);
        config.setBackoffFactor(configDto.getBackoffFactor() != null ? configDto.getBackoffFactor() : 2.0);
        config.setMaxInterval(configDto.getMaxInterval() != null ? configDto.getMaxInterval() : 3600);
        config.setMaxAge(configDto.getMaxAge() != null ? configDto.getMaxAge() : 86400);
        config.setActive(true);
        config.setCreatedAt(LocalDateTime.now());
        config.setUpdatedAt(LocalDateTime.now());
        
        // Convert headers to JSON string if provided
        if (configDto.getHeaders() != null) {
            try {
                config.setHeaders(objectMapper.writeValueAsString(configDto.getHeaders()));
            } catch (JsonProcessingException e) {
                logger.error("Error serializing headers", e);
                throw new WebhookProcessingException("Error processing webhook headers: " + e.getMessage());
            }
        }
        
        // Create initial health stats record
        WebhookConfig savedConfig = webhookConfigRepository.save(config);
        
        WebhookHealthStats healthStats = new WebhookHealthStats();
        healthStats.setWebhookConfigId(savedConfig.getId());
        healthStats.setWebhookName(savedConfig.getName());
        healthStats.setTotalDelivered(0);
        healthStats.setTotalFailed(0);
        healthStats.setAverageResponseTime(0);
        healthStats.setLastUpdated(LocalDateTime.now());
        healthStatsRepository.save(healthStats);
        
        return savedConfig;
    }
    
    /**
     * Update an existing webhook configuration
     * 
     * @param id The webhook configuration ID
     * @param configDto Updated configuration data
     * @return The updated webhook configuration
     * @throws ResourceNotFoundException if webhook not found
     */
    @Transactional
    public WebhookConfig updateWebhookConfig(UUID id, WebhookConfigDto configDto) {
        WebhookConfig existingConfig = webhookConfigRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Webhook configuration not found: " + id));
        
        if (configDto.getName() != null) {
            // Check if new name conflicts with existing webhook (excluding current one)
            webhookConfigRepository.findByName(configDto.getName())
                    .ifPresent(config -> {
                        if (!config.getId().equals(id)) {
                            throw new ResourceAlreadyExistsException("Webhook with name '" + configDto.getName() + "' already exists");
                        }
                    });
            existingConfig.setName(configDto.getName());
            
            // Update health stats reference if name changes
            healthStatsRepository.findByWebhookConfigId(id).ifPresent(stats -> {
                stats.setWebhookName(configDto.getName());
                healthStatsRepository.save(stats);
            });
        }
        
        if (configDto.getTargetUrl() != null) {
            existingConfig.setTargetUrl(configDto.getTargetUrl());
        }
        
        if (configDto.getSecret() != null) {
            existingConfig.setSecret(configDto.getSecret());
        }
        
        if (configDto.getMaxRetries() != null) {
            existingConfig.setMaxRetries(configDto.getMaxRetries());
        }
        
        if (configDto.getBackoffStrategy() != null) {
            existingConfig.setBackoffStrategy(configDto.getBackoffStrategy());
        }
        
        if (configDto.getInitialInterval() != null) {
            existingConfig.setInitialInterval(configDto.getInitialInterval());
        }
        
        if (configDto.getBackoffFactor() != null) {
            existingConfig.setBackoffFactor(configDto.getBackoffFactor());
        }
        
        if (configDto.getMaxInterval() != null) {
            existingConfig.setMaxInterval(configDto.getMaxInterval());
        }
        
        if (configDto.getMaxAge() != null) {
            existingConfig.setMaxAge(configDto.getMaxAge());
        }
        
        if (configDto.getActive() != null) {
            existingConfig.setActive(configDto.getActive());
        }
        
        if (configDto.getHeaders() != null) {
            try {
                existingConfig.setHeaders(objectMapper.writeValueAsString(configDto.getHeaders()));
            } catch (JsonProcessingException e) {
                logger.error("Error serializing headers", e);
                throw new WebhookProcessingException("Error processing webhook headers: " + e.getMessage());
            }
        }
        
        existingConfig.setUpdatedAt(LocalDateTime.now());
        return webhookConfigRepository.save(existingConfig);
    }
    
    /**
     * Delete webhook configuration
     * 
     * @param id The webhook configuration ID
     * @throws ResourceNotFoundException if webhook not found
     */
    @Transactional
    public void deleteWebhookConfig(UUID id) {
        WebhookConfig config = webhookConfigRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Webhook configuration not found: " + id));
        
        // Soft delete by marking inactive
        config.setActive(false);
        config.setUpdatedAt(LocalDateTime.now());
        webhookConfigRepository.save(config);
    }
    
    /**
     * Receive a webhook event and queue it for delivery
     * 
     * @param webhookName Name of the target webhook
     * @param webhookRequestDto The webhook payload
     * @return The created message ID and status
     * @throws ResourceNotFoundException if webhook not found
     */
    @Transactional
    public Map<String, Object> receiveWebhook(String webhookName, WebhookRequestDto webhookRequestDto) {
        WebhookConfig webhookConfig = getWebhookConfigByName(webhookName);
        
        // Generate signature for the payload
        String payloadString;
        try {
            payloadString = objectMapper.writeValueAsString(webhookRequestDto.getPayload());
        } catch (JsonProcessingException e) {
            logger.error("Error serializing payload", e);
            throw new WebhookProcessingException("Error processing webhook payload: " + e.getMessage());
        }
        
        String signature = hmacService.generateSignature(payloadString, webhookConfig.getSecret());
        
        // Create message record
        Message message = new Message();
        message.setWebhookConfig(webhookConfig);
        message.setPayload(payloadString);
        message.setTargetUrl(webhookConfig.getTargetUrl());
        message.setStatus("pending");
        message.setSignature(signature);
        message.setHeaders(webhookConfig.getHeaders());
        message.setRetryCount(0);
        message.setCreatedAt(LocalDateTime.now());
        message.setUpdatedAt(LocalDateTime.now());
        
        Message savedMessage = messageRepository.save(message);
        
        // In direct mode, process message immediately
        if (directMode) {
            logger.info("Direct mode: processing message {} immediately", savedMessage.getId());
            messageSenderService.processMessageAsync(savedMessage.getId());
        } else {
            // Publish to Kafka for processing
            messageProducer.sendToMainTopic(savedMessage.getId());
            logger.info("Webhook message received and queued in Kafka: {}", savedMessage.getId());
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("message_id", savedMessage.getId().toString());
        result.put("status", savedMessage.getStatus());
        
        return result;
    }
    
    /**
     * Get message details including delivery attempts
     * 
     * @param messageId The message ID
     * @return Message details
     * @throws ResourceNotFoundException if message not found
     */
    public Map<String, Object> getMessageStatus(UUID messageId) {
        Message message = messageRepository.findByIdWithDeliveryAttempts(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found: " + messageId));
        
        Map<String, Object> response = new HashMap<>();
        response.put("message_id", message.getId().toString());
        response.put("webhook_name", message.getWebhookConfig().getName());
        response.put("status", message.getStatus());
        response.put("created_at", message.getCreatedAt());
        response.put("updated_at", message.getUpdatedAt());
        response.put("retry_count", message.getRetryCount());
        
        if (message.getNextRetry() != null) {
            response.put("next_retry", message.getNextRetry());
        }
        
        if (message.getLastError() != null) {
            response.put("last_error", message.getLastError());
        }
        
        if (message.getDeliveryAttempts() != null && !message.getDeliveryAttempts().isEmpty()) {
            List<Map<String, Object>> attempts = message.getDeliveryAttempts().stream()
                    .map(attempt -> {
                        Map<String, Object> attemptMap = new HashMap<>();
                        attemptMap.put("id", attempt.getId().toString());
                        attemptMap.put("attempt_number", attempt.getAttemptNumber());
                        attemptMap.put("timestamp", attempt.getTimestamp());
                        attemptMap.put("status_code", attempt.getStatusCode());
                        attemptMap.put("response_body", attempt.getResponseBody());
                        attemptMap.put("error", attempt.getError());
                        attemptMap.put("request_duration", attempt.getRequestDuration());
                        return attemptMap;
                    })
                    .collect(Collectors.toList());
            response.put("delivery_attempts", attempts);
        }
        
        return response;
    }
    
    /**
     * Check for pending messages in the database that need processing
     * 
     * @return Count of pending messages found and processed
     */
    @Transactional(readOnly = true)
    public int checkPendingMessages() {
        logger.info("Checking for pending messages in database...");
        
        // Get all pending messages
        List<Message> pendingMessages = messageRepository.findByStatus("pending");
        logger.info("Found {} pending messages in database", pendingMessages.size());
        
        // Get all scheduled failed messages that should be retried
        LocalDateTime now = LocalDateTime.now();
        List<Message> retryMessages = messageRepository.findMessagesForRetry(now);
        logger.info("Found {} messages ready for retry in database", retryMessages.size());
        
        // Process messages
        if (directMode) {
            // In direct mode, process messages directly
            for (Message message : pendingMessages) {
                messageSenderService.processMessageAsync(message.getId());
                logger.info("Direct mode: processing message {}", message.getId());
            }
            
            for (Message message : retryMessages) {
                messageSenderService.processMessageAsync(message.getId());
                logger.info("Direct mode: processing retry message {}", message.getId());
            }
        } else {
            // In Kafka mode, publish to appropriate topics
            for (Message message : pendingMessages) {
                messageProducer.sendToMainTopic(message.getId());
                logger.info("Published pending message {} to Kafka for processing", message.getId());
            }
            
            for (Message message : retryMessages) {
                messageProducer.sendToRetryTopic(message.getId());
                logger.info("Published retry message {} to Kafka retry topic", message.getId());
            }
        }
        
        return pendingMessages.size() + retryMessages.size();
    }
    
    /**
     * Get webhook configuration with its health statistics
     * 
     * @param webhookName The webhook name
     * @return Webhook configuration with health stats
     * @throws ResourceNotFoundException if webhook not found
     */
    public Map<String, Object> getWebhookWithStats(String webhookName) {
        WebhookConfig config = getWebhookConfigByName(webhookName);
        WebhookHealthStats stats = healthStatsRepository.findByWebhookName(webhookName)
                .orElse(null);
        
        Map<String, Object> result = new HashMap<>();
        result.put("id", config.getId().toString());
        result.put("name", config.getName());
        result.put("target_url", config.getTargetUrl());
        result.put("max_retries", config.getMaxRetries());
        result.put("backoff_strategy", config.getBackoffStrategy());
        result.put("initial_interval", config.getInitialInterval());
        result.put("backoff_factor", config.getBackoffFactor());
        result.put("max_interval", config.getMaxInterval());
        result.put("max_age", config.getMaxAge());
        result.put("created_at", config.getCreatedAt());
        result.put("updated_at", config.getUpdatedAt());
        result.put("active", config.isActive());
        
        if (stats != null) {
            Map<String, Object> healthStats = new HashMap<>();
            healthStats.put("webhook_config_id", stats.getWebhookConfigId().toString());
            healthStats.put("total_delivered", stats.getTotalDelivered());
            healthStats.put("total_failed", stats.getTotalFailed());
            healthStats.put("average_response_time", stats.getAverageResponseTime());
            healthStats.put("last_updated", stats.getLastUpdated());
            
            result.put("health_stats", healthStats);
        }
        
        return result;
    }
}