package com.sg.webhookservice.presentation.mapper;

import com.sg.webhookservice.domain.entity.WebhookHealthStats;
import com.sg.webhookservice.presentation.dto.WebhookHealthStatsDto;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Mapper para convertir entre entidades WebhookHealthStats y DTOs.
 */
@Component
public class WebhookHealthStatsMapper {

    /**
     * Convierte una entidad a un DTO.
     *
     * @param entity La entidad a convertir
     * @return El DTO WebhookHealthStatsDto
     */
    public WebhookHealthStatsDto toDto(WebhookHealthStats entity) {
        if (entity == null) {
            return null;
        }

        WebhookHealthStatsDto dto = new WebhookHealthStatsDto();
        dto.setId(entity.getId());
        dto.setWebhookConfigId(entity.getWebhookConfigId());
        dto.setWebhookName(entity.getWebhookName());
        dto.setTotalSent(entity.getTotalSent());
        dto.setTotalDelivered(entity.getTotalDelivered());
        dto.setTotalFailed(entity.getTotalFailed());
        dto.setAvgResponseTime(entity.getAvgResponseTime());
        dto.setLastSuccessTime(entity.getLastSuccessTime());
        dto.setLastErrorTime(entity.getLastErrorTime());
        dto.setLastError(entity.getLastError());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());

        return dto;
    }

    /**
     * Convierte un DTO a una entidad.
     *
     * @param dto El DTO a convertir
     * @return La entidad WebhookHealthStats
     */
    public WebhookHealthStats toEntity(WebhookHealthStatsDto dto) {
        if (dto == null) {
            return null;
        }

        WebhookHealthStats entity = new WebhookHealthStats();
        entity.setId(dto.getId());
        entity.setWebhookConfigId(dto.getWebhookConfigId());
        entity.setWebhookName(dto.getWebhookName());
        entity.setTotalSent(dto.getTotalSent());
        entity.setTotalDelivered(dto.getTotalDelivered());
        entity.setTotalFailed(dto.getTotalFailed());
        entity.setAvgResponseTime(dto.getAvgResponseTime());
        entity.setLastSuccessTime(dto.getLastSuccessTime());
        entity.setLastErrorTime(dto.getLastErrorTime());
        entity.setLastError(dto.getLastError());
        entity.setCreatedAt(dto.getCreatedAt());
        entity.setUpdatedAt(dto.getUpdatedAt());

        return entity;
    }

    /**
     * Convierte una lista de entidades a una lista de DTOs.
     *
     * @param entities Lista de entidades a convertir
     * @return Lista de DTOs
     */
    public List<WebhookHealthStatsDto> toDtoList(List<WebhookHealthStats> entities) {
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
    public WebhookHealthStats updateEntityFromDto(WebhookHealthStats entity, WebhookHealthStatsDto dto) {
        if (entity == null || dto == null) {
            return entity;
        }

        if (dto.getWebhookName() != null) {
            entity.setWebhookName(dto.getWebhookName());
        }

        if (dto.getTotalSent() >= 0) {
            entity.setTotalSent(dto.getTotalSent());
        }

        if (dto.getTotalDelivered() >= 0) {
            entity.setTotalDelivered(dto.getTotalDelivered());
        }

        if (dto.getTotalFailed() >= 0) {
            entity.setTotalFailed(dto.getTotalFailed());
        }

        if (dto.getAvgResponseTime() >= 0) {
            entity.setAvgResponseTime(dto.getAvgResponseTime());
        }

        if (dto.getLastSuccessTime() != null) {
            entity.setLastSuccessTime(dto.getLastSuccessTime());
        }

        if (dto.getLastErrorTime() != null) {
            entity.setLastErrorTime(dto.getLastErrorTime());
        }

        if (dto.getLastError() != null) {
            entity.setLastError(dto.getLastError());
        }

        return entity;
    }

    /**
     * Crea una nueva instancia de estadísticas de salud para un webhook.
     *
     * @param webhookConfigId ID del webhook
     * @param webhookName Nombre del webhook
     * @return Nueva entidad de estadísticas de salud
     */
    public WebhookHealthStats createInitialStats(String webhookConfigId, String webhookName) {
        WebhookHealthStats stats = new WebhookHealthStats();
        stats.setWebhookConfigId(java.util.UUID.fromString(webhookConfigId));
        stats.setWebhookName(webhookName);
        stats.setTotalSent(0);
        stats.setTotalDelivered(0);
        stats.setTotalFailed(0);
        stats.setAvgResponseTime(0);
        stats.setCreatedAt(java.time.OffsetDateTime.now());
        stats.setUpdatedAt(java.time.OffsetDateTime.now());

        return stats;
    }

    /**
     * Actualiza las estadísticas con un intento de entrega exitoso.
     *
     * @param stats Estadísticas a actualizar
     * @param responseTime Tiempo de respuesta en milisegundos
     * @return Estadísticas actualizadas
     */
    public WebhookHealthStats recordSuccessfulDelivery(WebhookHealthStats stats, long responseTime) {
        stats.setTotalSent(stats.getTotalSent() + 1);
        stats.setTotalDelivered(stats.getTotalDelivered() + 1);
        stats.setLastSuccessTime(java.time.OffsetDateTime.now());

        // Actualizar tiempo de respuesta promedio con un peso de 70% histórico, 30% actual
        double currentAvg = stats.getAvgResponseTime();
        if (currentAvg == 0) {
            stats.setAvgResponseTime(responseTime);
        } else {
            stats.setAvgResponseTime((currentAvg * 0.7) + (responseTime * 0.3));
        }

        stats.setUpdatedAt(java.time.OffsetDateTime.now());

        return stats;
    }

    /**
     * Actualiza las estadísticas con un intento de entrega fallido.
     *
     * @param stats Estadísticas a actualizar
     * @param errorMessage Mensaje de error
     * @return Estadísticas actualizadas
     */
    public WebhookHealthStats recordFailedDelivery(WebhookHealthStats stats, String errorMessage) {
        stats.setTotalSent(stats.getTotalSent() + 1);
        stats.setTotalFailed(stats.getTotalFailed() + 1);
        stats.setLastErrorTime(java.time.OffsetDateTime.now());
        stats.setLastError(errorMessage);
        stats.setUpdatedAt(java.time.OffsetDateTime.now());

        return stats;
    }

    /**
     * Calcula y actualiza métricas derivadas para las estadísticas de salud.
     *
     * @param stats Estadísticas a actualizar
     * @return Estadísticas actualizadas con métricas derivadas
     */
    public WebhookHealthStats calculateDerivedMetrics(WebhookHealthStats stats) {
        // Aquí se podrían calcular métricas adicionales como:
        // - Tasa de éxito
        // - Estado de salud basado en umbrales
        // - Tendencias en el tiempo
        // - Alertas basadas en cambios drásticos

        stats.setUpdatedAt(java.time.OffsetDateTime.now());
        return stats;
    }

    /**
     * Combina estadísticas de múltiples webhooks para obtener métricas globales.
     *
     * @param statsList Lista de estadísticas a combinar
     * @return DTO con estadísticas globales combinadas
     */
    public WebhookHealthStatsDto combineStats(List<WebhookHealthStats> statsList) {
        if (statsList == null || statsList.isEmpty()) {
            return null;
        }

        WebhookHealthStatsDto globalStats = new WebhookHealthStatsDto();
        globalStats.setWebhookName("Global");
        globalStats.setTotalSent(0);
        globalStats.setTotalDelivered(0);
        globalStats.setTotalFailed(0);
        double totalResponseTime = 0;
        int webhooksWithResponseTime = 0;

        for (WebhookHealthStats stats : statsList) {
            globalStats.setTotalSent(globalStats.getTotalSent() + stats.getTotalSent());
            globalStats.setTotalDelivered(globalStats.getTotalDelivered() + stats.getTotalDelivered());
            globalStats.setTotalFailed(globalStats.getTotalFailed() + stats.getTotalFailed());

            if (stats.getAvgResponseTime() > 0) {
                totalResponseTime += stats.getAvgResponseTime();
                webhooksWithResponseTime++;
            }

            // Mantener el último error más reciente
            if (stats.getLastErrorTime() != null &&
                    (globalStats.getLastErrorTime() == null ||
                            stats.getLastErrorTime().isAfter(globalStats.getLastErrorTime()))) {
                globalStats.setLastErrorTime(stats.getLastErrorTime());
                globalStats.setLastError(stats.getLastError());
            }

            // Mantener el último éxito más reciente
            if (stats.getLastSuccessTime() != null &&
                    (globalStats.getLastSuccessTime() == null ||
                            stats.getLastSuccessTime().isAfter(globalStats.getLastSuccessTime()))) {
                globalStats.setLastSuccessTime(stats.getLastSuccessTime());
            }
        }

        // Calcular tiempo de respuesta promedio global
        if (webhooksWithResponseTime > 0) {
            globalStats.setAvgResponseTime(totalResponseTime / webhooksWithResponseTime);
        }

        return globalStats;
    }
}