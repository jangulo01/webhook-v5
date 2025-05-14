package com.sg.webhookservice.service;

import com.sg.webhookservice.presentation.dto.WebhookConfigDto;
import com.sg.webhookservice.presentation.dto.WebhookHealthStatsDto;
import com.sg.webhookservice.presentation.exception.ResourceNotFoundException;


import java.util.List;
import java.util.UUID;

/**
 * Interfaz para el servicio de gestión de configuraciones de webhook.
 * Define operaciones para crear, actualizar, eliminar y consultar webhooks.
 */
public interface WebhookConfigService {

    /**
     * Obtiene todas las configuraciones de webhook.
     *
     * @return Lista de configuraciones
     */
    List<WebhookConfigDto> getAllWebhookConfigs();

    /**
     * Obtiene todas las configuraciones de webhook activas.
     *
     * @return Lista de configuraciones activas
     */
    List<WebhookConfigDto> getActiveWebhookConfigs();

    /**
     * Busca una configuración de webhook por ID.
     *
     * @param id ID de la configuración
     * @return Configuración encontrada
     * @throws com.sg.webhookservice.presentation.exception.ResourceNotFoundException si no existe
     */
    WebhookConfigDto getWebhookConfigById(UUID id);

    /**
     * Busca una configuración de webhook por nombre.
     *
     * @param name Nombre de la configuración
     * @return Configuración encontrada
     * @throws com.sg.webhookservice.presentation.exception.ResourceNotFoundException si no existe
     */
    WebhookConfigDto getWebhookConfigByName(String name);

    /**
     * Crea una nueva configuración de webhook.
     *
     * @param configDto Datos de la configuración
     * @return Configuración creada
     * @throws com.sg.webhookservice.presentation.exception.ResourceAlreadyExistsException si ya existe
     * @throws jakarta.validation.ValidationException si hay errores de validación
     */
    WebhookConfigDto createWebhookConfig(WebhookConfigDto configDto);

    /**
     * Actualiza una configuración de webhook existente.
     *
     * @param id ID de la configuración a actualizar
     * @param configDto Nuevos datos
     * @return Configuración actualizada
     * @throws com.sg.webhookservice.presentation.exception.ResourceNotFoundException si no existe
     * @throws jakarta.validation.ValidationException si hay errores de validación
     */
    WebhookConfigDto updateWebhookConfig(UUID id, WebhookConfigDto configDto);

    /**
     * Elimina una configuración de webhook.
     *
     * @param id ID de la configuración a eliminar
     * @throws com.sg.webhookservice.presentation.exception.ResourceNotFoundException si no existe
     */
    void deleteWebhookConfig(UUID id);

    /**
     * Activa o desactiva una configuración de webhook.
     *
     * @param id ID de la configuración
     * @param active true para activar, false para desactivar
     * @return Configuración actualizada
     * @throws com.sg.webhookservice.presentation.exception.ResourceNotFoundException si no existe
     */
    WebhookConfigDto setWebhookActive(UUID id, boolean active);

    /**
     * Duplica una configuración de webhook con un nuevo nombre.
     *
     * @param id ID de la configuración a duplicar
     * @param newName Nuevo nombre para la copia
     * @return Copia creada
     * @throws com.sg.webhookservice.presentation.exception.ResourceNotFoundException si no existe la original
     * @throws com.sg.webhookservice.presentation.exception.ResourceAlreadyExistsException si ya existe el nuevo nombre
     */
    WebhookConfigDto duplicateWebhookConfig(UUID id, String newName);

    /**
     * Obtiene las estadísticas de salud de un webhook.
     *
     * @param id ID de la configuración
     * @return Estadísticas de salud
     * @throws com.sg.webhookservice.presentation.exception.ResourceNotFoundException si no existe
     */
    WebhookHealthStatsDto getWebhookHealthStats(UUID id);
}
