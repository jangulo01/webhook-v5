package com.sg.webhookservice.domain.repository.impl;

import com.sg.webhookservice.domain.entity.WebhookHealthStats;
import com.sg.webhookservice.domain.repository.WebhookHealthStatsRepository;
import com.sg.webhookservice.domain.repository.SpringDataWebhookHealthStatsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementación del repositorio para la entidad WebhookHealthStats.
 * Delega las operaciones al SpringDataWebhookHealthStatsRepository.
 */
@Component
@RequiredArgsConstructor
public class WebhookHealthStatsRepositoryImpl implements WebhookHealthStatsRepository {

    private final SpringDataWebhookHealthStatsRepository springDataRepository;

    @Override
    public Optional<WebhookHealthStats> findByWebhookConfigId(UUID webhookConfigId) {
        return springDataRepository.findByWebhookConfigId(webhookConfigId);
    }

    @Override
    public Optional<WebhookHealthStats> findByWebhookName(String webhookName) {
        return springDataRepository.findByWebhookName(webhookName);
    }

    @Override
    public List<WebhookHealthStats> findAllOrderBySuccessRate() {
        return springDataRepository.findAllOrderBySuccessRate();
    }

    @Override
    @Transactional
    public int recordSuccessfulDelivery(UUID webhookConfigId, long responseTimeMs) {
        return springDataRepository.recordSuccessfulDelivery(webhookConfigId, responseTimeMs);
    }

    @Override
    @Transactional
    public int recordFailedDelivery(UUID webhookConfigId, String errorMessage) {
        return springDataRepository.recordFailedDelivery(webhookConfigId, errorMessage);
    }

    @Override
    @Transactional
    public int updateWebhookName(UUID webhookConfigId, String newName) {
        return springDataRepository.updateWebhookName(webhookConfigId, newName);
    }

    @Override
    public List<WebhookHealthStats> findUnhealthyWebhooks(int minimumSent, double successRateThreshold) {
        return springDataRepository.findUnhealthyWebhooks(minimumSent, successRateThreshold);
    }

    @Override
    public Object[] getGlobalStats() {
        return springDataRepository.getGlobalStats();
    }

    @Override
    public List<WebhookHealthStats> findInactiveWebhooks(OffsetDateTime cutoffTime) {
        return springDataRepository.findInactiveWebhooks(cutoffTime);
    }

    @Override
    public WebhookHealthStats save(WebhookHealthStats entity) {
        return springDataRepository.save(entity);
    }

    @Override
    public Optional<WebhookHealthStats> findById(UUID id) {
        return springDataRepository.findById(id);
    }

    @Override
    public List<WebhookHealthStats> findAll() {
        return springDataRepository.findAll();
    }

    @Override
    public void deleteById(UUID id) {
        springDataRepository.deleteById(id);
    }

    @Override
    public boolean existsById(UUID id) {
        return springDataRepository.existsById(id);
    }
}