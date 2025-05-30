package com.sg.webhookservice.domain.repository;

import com.sg.webhookservice.domain.entity.WebhookConfig;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Interfaz de repositorio para la entidad WebhookConfig.
 * Define operaciones específicas para webhooks.
 */
public interface WebhookConfigRepository extends Repository<WebhookConfig> {

    /**
     * Busca una configuración de webhook por su nombre.
     *
     * @param name Nombre único del webhook
     * @return Configuración encontrada o Optional vacío
     */
    Optional<WebhookConfig> findByName(String name);

    /**
     * Busca una configuración de webhook por su nombre y estado activo.
     *
     * @param name Nombre único del webhook
     * @return Configuración encontrada o Optional vacío
     */
    Optional<WebhookConfig> findByNameAndActiveTrue(String name);

    /**
     * Obtiene todas las configuraciones de webhook activas.
     *
     * @return Lista de configuraciones activas
     */
    List<WebhookConfig> findByActiveTrue();

    /**
     * Activa o desactiva una configuración de webhook.
     *
     * @param id ID de la configuración
     * @param active true para activar, false para desactivar
     */
    void setActive(UUID id, boolean active);

    /**
     * Desactiva una configuración de webhook (actualiza el campo active a false).
     *
     * @param id ID de la configuración a desactivar
     * @return Número de filas afectadas (1 si se realizó la actualización)
     */
    int deactivateWebhook(UUID id);

    /**
     * Activa una configuración de webhook (actualiza el campo active a true).
     *
     * @param id ID de la configuración a activar
     * @return Número de filas afectadas (1 si se realizó la actualización)
     */
    int activateWebhook(UUID id);
}