package com.sg.webhookservice.domain.repository.impl;

import com.sg.webhookservice.domain.entity.WebhookConfig;
import com.sg.webhookservice.domain.repository.WebhookConfigRepository;
import com.sg.webhookservice.domain.repository.SpringDataWebhookConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementación del repositorio para la entidad WebhookConfig.
 * Delega las operaciones al SpringDataWebhookConfigRepository.
 */
@Component
@RequiredArgsConstructor
public class WebhookConfigRepositoryImpl implements WebhookConfigRepository {

    private final SpringDataWebhookConfigRepository springDataRepository;

    @Override
    public Optional<WebhookConfig> findById(UUID id) {
        return springDataRepository.findById(id);
    }

    @Override
    public List<WebhookConfig> findAll() {
        return springDataRepository.findAll();
    }

    @Override
    public WebhookConfig save(WebhookConfig entity) {
        return springDataRepository.save(entity);
    }

    @Override
    public void deleteById(UUID id) {
        springDataRepository.deleteById(id);
    }

    @Override
    public boolean existsById(UUID id) {
        return springDataRepository.existsById(id);
    }

    @Override
    public Optional<WebhookConfig> findByName(String name) {
        return springDataRepository.findByName(name);
    }

    @Override
    public Optional<WebhookConfig> findByNameAndActiveTrue(String name) {
        return springDataRepository.findByNameAndActiveTrue(name);
    }

    @Override
    public List<WebhookConfig> findByActiveTrue() {
        return springDataRepository.findByActiveTrue();
    }

    @Override
    public void setActive(UUID id, boolean active) {
        if (active) {
            springDataRepository.activateWebhook(id);
        } else {
            springDataRepository.deactivateWebhook(id);
        }
    }

    @Override
    @Transactional
    public int deactivateWebhook(UUID id) {
        return springDataRepository.deactivateWebhook(id);
    }

    @Override
    @Transactional
    public int activateWebhook(UUID id) {
        return springDataRepository.activateWebhook(id);
    }
}