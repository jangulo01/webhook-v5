package com.sg.webhookservice.presentation.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sg.webhookservice.domain.entity.WebhookConfig;
import com.sg.webhookservice.presentation.dto.WebhookConfigDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Mapper para convertir entre entidades WebhookConfig y DTOs.
 */
@Component
@RequiredArgsConstructor
public class WebhookConfigMapper {

    private final ObjectMapper objectMapper;

    /**
     * Convierte un DTO a una entidad.
     *
     * @param dto El DTO a convertir
     * @return La entidad WebhookConfig
     */
    public WebhookConfig toEntity(WebhookConfigDto dto) {
        if (dto == null) {
            return null;
        }

        WebhookConfig entity = new WebhookConfig();
        entity.setId(dto.getId());
        entity.setName(dto.getName());
        entity.setTargetUrl(dto.getTargetUrl());
        entity.setSecret(dto.getSecret());

        // Establecer valores predeterminados si son nulos
        dto.setDefaultsIfNull();

        entity.setMaxRetries(dto.getMaxRetries());
        entity.setBackoffStrategy(dto.getBackoffStrategy());
        entity.setInitialInterval(dto.getInitialInterval());
        entity.setBackoffFactor(dto.getBackoffFactor());
        entity.setMaxInterval(dto.getMaxInterval());
        entity.setMaxAge(dto.getMaxAge());
        entity.setActive(dto.getActive());

        // Convertir headers de Map a JSON si existen
        if (dto.getHeaders() != null && !dto.getHeaders().isEmpty()) {
            try {
                entity.setHeaders(objectMapper.writeValueAsString(dto.getHeaders()));
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Error al serializar headers", e);
            }
        }

        return entity;
    }

    /**
     * Convierte una entidad a un DTO.
     *
     * @param entity La entidad a convertir
     * @return El DTO WebhookConfigDto
     */
    public WebhookConfigDto toDto(WebhookConfig entity) {
        if (entity == null) {
            return null;
        }

        WebhookConfigDto dto = new WebhookConfigDto();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setTargetUrl(entity.getTargetUrl());
        // No incluir el secreto en las respuestas por seguridad
        dto.setSecret(null);
        dto.setMaxRetries(entity.getMaxRetries());
        dto.setBackoffStrategy(entity.getBackoffStrategy());
        dto.setInitialInterval(entity.getInitialInterval());
        dto.setBackoffFactor(entity.getBackoffFactor());
        dto.setMaxInterval(entity.getMaxInterval());
        dto.setMaxAge(entity.getMaxAge());
        dto.setActive(entity.isActive());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());

        // Convertir headers de JSON a Map si existen
        if (entity.getHeaders() != null && !entity.getHeaders().isEmpty()) {
            try {
                dto.setHeaders(objectMapper.readValue(entity.getHeaders(),
                        new TypeReference<Map<String, String>>() {}));
            } catch (JsonProcessingException e) {
                // En caso de error, establecer un mapa vacío
                dto.setHeaders(Collections.emptyMap());
            }
        }

        return dto;
    }

    /**
     * Convierte una lista de entidades a una lista de DTOs.
     *
     * @param entities Lista de entidades a convertir
     * @return Lista de DTOs
     */
    public List<WebhookConfigDto> toDtoList(List<WebhookConfig> entities) {
        if (entities == null) {
            return Collections.emptyList();
        }

        return entities.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Actualiza una entidad existente con datos de un DTO.
     *
     * @param entity Entidad a actualizar
     * @param dto DTO con los datos nuevos
     * @return Entidad actualizada
     */
    public WebhookConfig updateEntityFromDto(WebhookConfig entity, WebhookConfigDto dto) {
        if (entity == null || dto == null) {
            return entity;
        }

        if (dto.getName() != null) {
            entity.setName(dto.getName());
        }

        if (dto.getTargetUrl() != null) {
            entity.setTargetUrl(dto.getTargetUrl());
        }

        if (dto.getSecret() != null) {
            entity.setSecret(dto.getSecret());
        }

        if (dto.getMaxRetries() != null) {
            entity.setMaxRetries(dto.getMaxRetries());
        }

        if (dto.getBackoffStrategy() != null) {
            entity.setBackoffStrategy(dto.getBackoffStrategy());
        }

        if (dto.getInitialInterval() != null) {
            entity.setInitialInterval(dto.getInitialInterval());
        }

        if (dto.getBackoffFactor() != null) {
            entity.setBackoffFactor(dto.getBackoffFactor());
        }

        if (dto.getMaxInterval() != null) {
            entity.setMaxInterval(dto.getMaxInterval());
        }

        if (dto.getMaxAge() != null) {
            entity.setMaxAge(dto.getMaxAge());
        }

        if (dto.getActive() != null) {
            entity.setActive(dto.getActive());
        }

        // Actualizar headers si se proporcionan
        if (dto.getHeaders() != null) {
            try {
                entity.setHeaders(objectMapper.writeValueAsString(dto.getHeaders()));
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Error al serializar headers", e);
            }
        }

        return entity;
    }
}