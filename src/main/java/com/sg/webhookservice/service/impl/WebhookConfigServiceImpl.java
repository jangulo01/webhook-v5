package com.sg.webhookservice.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sg.webhookservice.presentation.exception.ResourceAlreadyExistsException;
import com.sg.webhookservice.presentation.exception.ResourceNotFoundException;
import com.sg.webhookservice.presentation.exception.WebhookProcessingException;
import com.sg.webhookservice.service.WebhookConfigService;
import com.sg.webhookservice.domain.entity.WebhookConfig;
import com.sg.webhookservice.domain.entity.WebhookHealthStats;
import com.sg.webhookservice.domain.repository.WebhookConfigRepository;
import com.sg.webhookservice.domain.repository.WebhookHealthStatsRepository;
import com.sg.webhookservice.presentation.dto.WebhookConfigDto;
import com.sg.webhookservice.presentation.dto.WebhookHealthStatsDto;
import com.sg.webhookservice.presentation.mapper.WebhookConfigMapper;
import com.sg.webhookservice.presentation.mapper.WebhookHealthStatsMapper;
import com.sg.webhookservice.presentation.validation.WebhookConfigValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.ValidationUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementación del servicio de gestión de configuraciones de webhook.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WebhookConfigServiceImpl implements WebhookConfigService {

    private final WebhookConfigRepository webhookConfigRepository;
    private final WebhookHealthStatsRepository webhookHealthStatsRepository;
    private final WebhookConfigMapper webhookConfigMapper;
    private final WebhookHealthStatsMapper webhookHealthStatsMapper;
    private final WebhookConfigValidator webhookConfigValidator;
    private final ObjectMapper objectMapper;

    @Value("${webhook.default-secret:test-secret}")
    private String defaultSecret;

    @Override
    @Transactional(readOnly = true)
    public List<WebhookConfigDto> getAllWebhookConfigs() {
        List<WebhookConfig> configs = webhookConfigRepository.findAll();
        return webhookConfigMapper.toDtoList(configs);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WebhookConfigDto> getActiveWebhookConfigs() {
        List<WebhookConfig> configs = webhookConfigRepository.findByActiveTrue();
        return webhookConfigMapper.toDtoList(configs);
    }

    @Override
    @Transactional(readOnly = true)
    public WebhookConfigDto getWebhookConfigById(UUID id) {
        WebhookConfig config = webhookConfigRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Webhook configuration not found", "webhook", id.toString()));
        return webhookConfigMapper.toDto(config);
    }

    @Override
    @Transactional(readOnly = true)
    public WebhookConfigDto getWebhookConfigByName(String name) {
        WebhookConfig config = webhookConfigRepository.findByName(name)
                .orElseThrow(() -> new ResourceNotFoundException("Webhook configuration not found", "webhook", name));
        return webhookConfigMapper.toDto(config);
    }

    @Override
    @Transactional
    public WebhookConfigDto createWebhookConfig(WebhookConfigDto configDto) {
        // Validar configuración con validador personalizado
        Errors errors = new BeanPropertyBindingResult(configDto, "webhookConfig");
        ValidationUtils.invokeValidator(webhookConfigValidator, configDto, errors);

        if (errors.hasErrors()) {
            String errorMessages = errors.getAllErrors().stream()
                    .map(error -> error.getDefaultMessage())
                    .collect(Collectors.joining("; "));
            throw new WebhookProcessingException(
                    "Validation error: " + errorMessages,
                    WebhookProcessingException.ProcessingPhase.VALIDATION,
                    configDto.getName(),
                    null
            );
        }

        // Verificar si ya existe un webhook con el mismo nombre
        if (webhookConfigRepository.findByName(configDto.getName()).isPresent()) {
            throw new ResourceAlreadyExistsException(
                    "Webhook with name '" + configDto.getName() + "' already exists",
                    "webhook",
                    configDto.getName()
            );
        }

        // Establecer valores predeterminados si no se proporcionan
        configDto.setDefaultsIfNull();

        // Convertir DTO a entidad
        WebhookConfig config = webhookConfigMapper.toEntity(configDto);

        // Guardar en la base de datos
        WebhookConfig savedConfig = webhookConfigRepository.save(config);

        // Crear estadísticas de salud iniciales
        WebhookHealthStats healthStats = WebhookHealthStats.createInitial(savedConfig);
        webhookHealthStatsRepository.save(healthStats);

        log.info("Created webhook configuration: {}", savedConfig.getName());

        return webhookConfigMapper.toDto(savedConfig);
    }

    @Override
    @Transactional
    public WebhookConfigDto updateWebhookConfig(UUID id, WebhookConfigDto configDto) {
        // Buscar configuración existente
        WebhookConfig existingConfig = webhookConfigRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Webhook configuration not found", "webhook", id.toString()));

        // Si se cambia el nombre, verificar que no exista otro webhook con ese nombre
        if (configDto.getName() != null && !configDto.getName().equals(existingConfig.getName())) {
            webhookConfigRepository.findByName(configDto.getName()).ifPresent(config -> {
                if (!config.getId().equals(id)) {
                    throw new ResourceAlreadyExistsException(
                            "Webhook with name '" + configDto.getName() + "' already exists",
                            "webhook",
                            configDto.getName()
                    );
                }
            });
        }

        // Actualizar la entidad con los datos del DTO
        WebhookConfig updatedConfig = webhookConfigMapper.updateEntityFromDto(existingConfig, configDto);

        // Guardar en la base de datos
        WebhookConfig savedConfig = webhookConfigRepository.save(updatedConfig);

        // Si se cambió el nombre, actualizar también en las estadísticas de salud
        if (configDto.getName() != null && !configDto.getName().equals(existingConfig.getName())) {
            webhookHealthStatsRepository.updateWebhookName(id, configDto.getName());
        }

        log.info("Updated webhook configuration: {}", savedConfig.getName());

        return webhookConfigMapper.toDto(savedConfig);
    }

    @Override
    @Transactional
    public void deleteWebhookConfig(UUID id) {
        // Verificar que exista
        if (!webhookConfigRepository.existsById(id)) {
            throw new ResourceNotFoundException("Webhook configuration not found", "webhook", id.toString());
        }

        // En lugar de eliminar físicamente, desactivar (soft delete)
        webhookConfigRepository.deactivateWebhook(id);

        log.info("Deactivated webhook configuration with ID: {}", id);
    }

    @Override
    @Transactional
    public WebhookConfigDto setWebhookActive(UUID id, boolean active) {
        // Buscar configuración existente
        WebhookConfig existingConfig = webhookConfigRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Webhook configuration not found", "webhook", id.toString()));

        // Si ya está en el estado deseado, no hacer nada
        if (existingConfig.isActive() == active) {
            return webhookConfigMapper.toDto(existingConfig);
        }

        // Actualizar estado activo
        existingConfig.setActive(active);
        WebhookConfig savedConfig = webhookConfigRepository.save(existingConfig);

        log.info("{} webhook configuration: {}", active ? "Activated" : "Deactivated", savedConfig.getName());

        return webhookConfigMapper.toDto(savedConfig);
    }

    @Override
    @Transactional
    public WebhookConfigDto duplicateWebhookConfig(UUID id, String newName) {
        // Buscar configuración existente
        WebhookConfig existingConfig = webhookConfigRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Webhook configuration not found", "webhook", id.toString()));

        // Verificar que no exista un webhook con el nuevo nombre
        if (webhookConfigRepository.findByName(newName).isPresent()) {
            throw new ResourceAlreadyExistsException(
                    "Webhook with name '" + newName + "' already exists",
                    "webhook",
                    newName
            );
        }

        // Crear copia
        WebhookConfig newConfig = existingConfig.duplicate(newName);
        WebhookConfig savedConfig = webhookConfigRepository.save(newConfig);

        // Crear estadísticas de salud iniciales para la copia
        WebhookHealthStats healthStats = WebhookHealthStats.createInitial(savedConfig);
        webhookHealthStatsRepository.save(healthStats);

        log.info("Duplicated webhook configuration '{}' to '{}'", existingConfig.getName(), newName);

        return webhookConfigMapper.toDto(savedConfig);
    }

    @Override
    @Transactional(readOnly = true)
    public WebhookHealthStatsDto getWebhookHealthStats(UUID id) {
        // Verificar que exista el webhook
        if (!webhookConfigRepository.existsById(id)) {
            throw new ResourceNotFoundException("Webhook configuration not found", "webhook", id.toString());
        }

        // Buscar estadísticas
        WebhookHealthStats stats = webhookHealthStatsRepository.findByWebhookConfigId(id)
                .orElseThrow(() -> new ResourceNotFoundException("Health statistics not found", "webhook_health_stats", id.toString()));

        return webhookHealthStatsMapper.toDto(stats);
    }

    /**
     * Convierte un mapa a JSON
     */
    private String convertMapToJson(Map<String, String> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            log.error("Error serializing headers map", e);
            throw new WebhookProcessingException(
                    "Error processing webhook headers: " + e.getMessage(),
                    WebhookProcessingException.ProcessingPhase.SERIALIZATION,
                    null,
                    null
            );
        }
    }
}