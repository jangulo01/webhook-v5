package com.sg.webhookservice.presentation.mapper;

import com.sg.webhookservice.domain.entity.DeliveryAttempt;
import com.sg.webhookservice.presentation.dto.DeliveryAttemptDto;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Mapper para convertir entre entidades DeliveryAttempt y DTOs.
 */
@Component
public class DeliveryAttemptMapper {

    /**
     * Convierte una entidad a un DTO.
     *
     * @param entity La entidad a convertir
     * @return El DTO DeliveryAttemptDto
     */
    public DeliveryAttemptDto toDto(DeliveryAttempt entity) {
        if (entity == null) {
            return null;
        }

        DeliveryAttemptDto dto = new DeliveryAttemptDto();
        dto.setId(entity.getId());
        dto.setMessageId(entity.getMessage().getId());
        dto.setAttemptNumber(entity.getAttemptNumber());
        dto.setTimestamp(entity.getTimestamp());
        dto.setStatusCode(entity.getStatusCode());
        dto.setResponseBody(entity.getResponseBody());
        dto.setError(entity.getError());
        dto.setRequestDuration(entity.getRequestDuration());

        return dto;
    }

    /**
     * Convierte un DTO a una entidad.
     * Este método es principalmente útil para pruebas.
     *
     * @param dto El DTO a convertir
     * @return La entidad DeliveryAttempt
     */
    public DeliveryAttempt toEntity(DeliveryAttemptDto dto) {
        if (dto == null) {
            return null;
        }

        DeliveryAttempt entity = new DeliveryAttempt();
        entity.setId(dto.getId());
        // Nota: message debe establecerse manualmente
        entity.setAttemptNumber(dto.getAttemptNumber());
        entity.setTimestamp(dto.getTimestamp());
        entity.setStatusCode(dto.getStatusCode());
        entity.setResponseBody(dto.getResponseBody());
        entity.setError(dto.getError());
        entity.setRequestDuration(dto.getRequestDuration());

        return entity;
    }

    /**
     * Convierte una lista de entidades a una lista de DTOs.
     *
     * @param entities Lista de entidades a convertir
     * @return Lista de DTOs
     */
    public List<DeliveryAttemptDto> toDtoList(List<DeliveryAttempt> entities) {
        if (entities == null) {
            return Collections.emptyList();
        }

        return entities.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }
}
