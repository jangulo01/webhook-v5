package com.sg.webhookservice.domain.repository.impl;

import com.sg.webhookservice.domain.entity.WebhookHealthStats;
import com.sg.webhookservice.domain.repository.WebhookHealthStatsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.FluentQuery;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Implementación del repositorio para la entidad WebhookHealthStats.
 */
@Component
@RequiredArgsConstructor
public class WebhookHealthStatsRepositoryImpl implements WebhookHealthStatsRepository {

    private final WebhookHealthStatsRepository delegate;

    @Override
    public Optional<WebhookHealthStats> findByWebhookConfigId(UUID webhookConfigId) {
        return delegate.findByWebhookConfigId(webhookConfigId);
    }

    @Override
    public Optional<WebhookHealthStats> findByWebhookName(String webhookName) {
        return delegate.findByWebhookName(webhookName);
    }

    @Override
    public List<WebhookHealthStats> findAllOrderBySuccessRate() {
        return delegate.findAllOrderBySuccessRate();
    }

    @Override
    @Transactional
    public int recordSuccessfulDelivery(UUID webhookConfigId, long responseTimeMs) {
        return delegate.recordSuccessfulDelivery(webhookConfigId, responseTimeMs);
    }

    @Override
    @Transactional
    public int recordFailedDelivery(UUID webhookConfigId, String errorMessage) {
        return delegate.recordFailedDelivery(webhookConfigId, errorMessage);
    }

    @Override
    @Transactional
    public int updateWebhookName(UUID webhookConfigId, String newName) {
        return delegate.updateWebhookName(webhookConfigId, newName);
    }

    @Override
    public List<WebhookHealthStats> findUnhealthyWebhooks(int minimumSent, double successRateThreshold) {
        return delegate.findUnhealthyWebhooks(minimumSent, successRateThreshold);
    }

    @Override
    public Object[] getGlobalStats() {
        return delegate.getGlobalStats();
    }

    @Override
    public List<WebhookHealthStats> findInactiveWebhooks(OffsetDateTime cutoffTime) {
        return delegate.findInactiveWebhooks(cutoffTime);
    }

    // Implementación de métodos heredados de JpaRepository

    @Override
    public void flush() {
        delegate.flush();
    }

    @Override
    public <S extends WebhookHealthStats> S saveAndFlush(S entity) {
        return delegate.saveAndFlush(entity);
    }

    @Override
    public <S extends WebhookHealthStats> List<S> saveAllAndFlush(Iterable<S> entities) {
        return delegate.saveAllAndFlush(entities);
    }

    @Override
    public void deleteAllInBatch(Iterable<WebhookHealthStats> entities) {
        delegate.deleteAllInBatch(entities);
    }

    @Override
    public void deleteAllByIdInBatch(Iterable<UUID> uuids) {
        delegate.deleteAllByIdInBatch(uuids);
    }

    @Override
    public void deleteAllInBatch() {
        delegate.deleteAllInBatch();
    }

    @Override
    public WebhookHealthStats getOne(UUID uuid) {
        return delegate.getOne(uuid);
    }

    @Override
    public WebhookHealthStats getById(UUID uuid) {
        return delegate.getById(uuid);
    }

    @Override
    public WebhookHealthStats getReferenceById(UUID uuid) {
        return delegate.getReferenceById(uuid);
    }

    @Override
    public <S extends WebhookHealthStats> Optional<S> findOne(Example<S> example) {
        return delegate.findOne(example);
    }

    @Override
    public <S extends WebhookHealthStats> List<S> findAll(Example<S> example) {
        return delegate.findAll(example);
    }

    @Override
    public <S extends WebhookHealthStats> List<S> findAll(Example<S> example, Sort sort) {
        return delegate.findAll(example, sort);
    }

    @Override
    public <S extends WebhookHealthStats> Page<S> findAll(Example<S> example, Pageable pageable) {
        return delegate.findAll(example, pageable);
    }

    @Override
    public <S extends WebhookHealthStats> long count(Example<S> example) {
        return delegate.count(example);
    }

    @Override
    public <S extends WebhookHealthStats> boolean exists(Example<S> example) {
        return delegate.exists(example);
    }

    @Override
    public <S extends WebhookHealthStats, R> R findBy(Example<S> example, Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) {
        return delegate.findBy(example, queryFunction);
    }

    @Override
    public <S extends WebhookHealthStats> S save(S entity) {
        return delegate.save(entity);
    }

    @Override
    public <S extends WebhookHealthStats> List<S> saveAll(Iterable<S> entities) {
        return delegate.saveAll(entities);
    }

    @Override
    public Optional<WebhookHealthStats> findById(UUID uuid) {
        return delegate.findById(uuid);
    }

    @Override
    public boolean existsById(UUID uuid) {
        return delegate.existsById(uuid);
    }

    @Override
    public List<WebhookHealthStats> findAll() {
        return delegate.findAll();
    }

    @Override
    public List<WebhookHealthStats> findAllById(Iterable<UUID> uuids) {
        return delegate.findAllById(uuids);
    }

    @Override
    public long count() {
        return delegate.count();
    }

    @Override
    public void deleteById(UUID uuid) {
        delegate.deleteById(uuid);
    }

    @Override
    public void delete(WebhookHealthStats entity) {
        delegate.delete(entity);
    }

    @Override
    public void deleteAllById(Iterable<? extends UUID> uuids) {
        delegate.deleteAllById(uuids);
    }

    @Override
    public void deleteAll(Iterable<? extends WebhookHealthStats> entities) {
        delegate.deleteAll(entities);
    }

    @Override
    public void deleteAll() {
        delegate.deleteAll();
    }

    @Override
    public List<WebhookHealthStats> findAll(Sort sort) {
        return delegate.findAll(sort);
    }

    @Override
    public Page<WebhookHealthStats> findAll(Pageable pageable) {
        return delegate.findAll(pageable);
    }
}