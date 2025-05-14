package com.sg.webhookservice.presentation.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sg.webhookservice.domain.entity.Message;
import com.sg.webhookservice.presentation.dto.DeliveryAttemptDto;
import com.sg.webhookservice.presentation.dto.MessageDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Mapper para convertir entre entidades Message y DTOs.
 */
@Component
@RequiredArgsConstructor
public class MessageMapper {

    private final ObjectMapper objectMapper;
    private final DeliveryAttemptMapper deliveryAttemptMapper;

    /**
     * Convierte una entidad a un DTO.
     *
     * @param entity La entidad a convertir
     * @return El DTO MessageDto
     */
    public MessageDto toDto(Message entity) {
        if (entity == null) {
            return null;
        }

        MessageDto dto = new MessageDto();
        dto.setId(entity.getId());
        dto.setWebhookConfigId(entity.getWebhookConfig().getId());
        dto.setWebhookName(entity.getWebhookConfig().getName());
        dto.setTargetUrl(entity.getTargetUrl());
        dto.setStatus(entity.getStatus().name());
        dto.setSignature(entity.getSignature());
        dto.setRetryCount(entity.getRetryCount());
        dto.setNextRetry(entity.getNextRetry());
        dto.setLastError(entity.getLastError());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());

        // No incluir el payload por defecto (puede ser grande)
        // dto.setPayload(entity.getPayload());

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

        // Convertir intentos de entrega si están cargados
        if (entity.getDeliveryAttempts() != null && !entity.getDeliveryAttempts().isEmpty()) {
            dto.setDeliveryAttempts(
                    entity.getDeliveryAttempts().stream()
                            .map(deliveryAttemptMapper::toDto)
                            .collect(Collectors.toList())
            );
        }

        return dto;
    }

    /**
     * Convierte un DTO a una entidad.
     * Este método es principalmente útil para pruebas, ya que normalmente
     * las entidades Message se crean a partir de solicitudes de webhook.
     *
     * @param dto El DTO a convertir
     * @return La entidad Message
     */
    public Message toEntity(MessageDto dto) {
        if (dto == null) {
            return null;
        }

        Message entity = new Message();
        entity.setId(dto.getId());
        // Nota: webhookConfig debe establecerse manualmente
        entity.setTargetUrl(dto.getTargetUrl());
        entity.setSignature(dto.getSignature());

        if (dto.getStatus() != null) {
            entity.setStatus(Message.MessageStatus.valueOf(dto.getStatus()));
        }

        entity.setRetryCount(dto.getRetryCount());
        entity.setNextRetry(dto.getNextRetry());
        entity.setLastError(dto.getLastError());
        entity.setPayload(dto.getPayload());

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
     * Convierte una lista de entidades a una lista de DTOs.
     *
     * @param entities Lista de entidades a convertir
     * @return Lista de DTOs
     */
    public List<MessageDto> toDtoList(List<Message> entities) {
        if (entities == null) {
            return Collections.emptyList();
        }

        return entities.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Convierte una entidad a un DTO incluyendo el payload.
     * Esta versión debe usarse con precaución ya que puede devolver grandes cantidades de datos.
     *
     * @param entity La entidad a convertir
     * @return DTO con payload incluido
     */
    public MessageDto toDtoWithPayload(Message entity) {
        MessageDto dto = toDto(entity);
        if (dto != null && entity != null) {
            dto.setPayload(entity.getPayload());
        }
        return dto;
    }
}
