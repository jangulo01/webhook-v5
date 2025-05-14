package com.sg.webhookservice.domain.repository;

import com.sg.webhookservice.domain.entity.WebhookConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio Spring Data JPA para la entidad WebhookConfig.
 * Esta interfaz extiende JpaRepository para proporcionar operaciones CRUD básicas
 * y también define métodos personalizados para gestionar configuraciones de webhook.
 */
@Repository
public interface SpringDataWebhookConfigRepository extends JpaRepository<WebhookConfig, UUID> {

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
     * Desactiva una configuración de webhook (actualiza el campo active a false).
     *
     * @param id ID de la configuración a desactivar
     * @return Número de filas afectadas (1 si se realizó la actualización)
     */
    @Modifying
    @Query("UPDATE WebhookConfig w SET w.active = false WHERE w.id = :id")
    int deactivateWebhook(@Param("id") UUID id);

    /**
     * Activa una configuración de webhook (actualiza el campo active a true).
     *
     * @param id ID de la configuración a activar
     * @return Número de filas afectadas (1 si se realizó la actualización)
     */
    @Modifying
    @Query("UPDATE WebhookConfig w SET w.active = true WHERE w.id = :id")
    int activateWebhook(@Param("id") UUID id);
}