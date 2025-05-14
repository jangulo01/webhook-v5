package com.sg.webhookservice.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Interfaz genérica para los repositorios del dominio.
 * Define operaciones básicas comunes a todos los repositorios.
 *
 * @param <T> Tipo de entidad
 */
public interface Repository<T> {

    /**
     * Encuentra una entidad por su ID.
     *
     * @param id ID de la entidad
     * @return Entidad encontrada o Optional vacío
     */
    Optional<T> findById(UUID id);

    /**
     * Obtiene todas las entidades.
     *
     * @return Lista de entidades
     */
    List<T> findAll();

    /**
     * Guarda una entidad.
     *
     * @param entity Entidad a guardar
     * @return Entidad guardada
     */
    T save(T entity);

    /**
     * Elimina una entidad por su ID.
     *
     * @param id ID de la entidad a eliminar
     */
    void deleteById(UUID id);

    /**
     * Verifica si existe una entidad con el ID dado.
     *
     * @param id ID a verificar
     * @return true si existe, false si no
     */
    boolean existsById(UUID id);
}