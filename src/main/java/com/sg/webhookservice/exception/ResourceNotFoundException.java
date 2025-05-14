package com.sg.webhookservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Excepción lanzada cuando un recurso solicitado no se encuentra
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException {

    private final String resourceType;
    private final String resourceId;

    /**
     * Constructor para la excepción ResourceNotFoundException
     *
     * @param message Mensaje descriptivo del error
     * @param resourceType Tipo de recurso no encontrado (ej: "webhook", "message")
     * @param resourceId Identificador del recurso buscado
     */
    public ResourceNotFoundException(String message, String resourceType, String resourceId) {
        super(message);
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }

    /**
     * Obtiene el tipo de recurso no encontrado
     *
     * @return Tipo de recurso
     */
    public String getResourceType() {
        return resourceType;
    }

    /**
     * Obtiene el identificador del recurso buscado
     *
     * @return Identificador del recurso
     */
    public String getResourceId() {
        return resourceId;
    }

    /**
     * Crea una excepción ResourceNotFoundException para un mensaje no encontrado
     *
     * @param messageId ID del mensaje no encontrado
     * @return Instancia de ResourceNotFoundException
     */
    public static ResourceNotFoundException messageNotFound(String messageId) {
        return new ResourceNotFoundException(
                "Mensaje no encontrado: " + messageId,
                "message",
                messageId
        );
    }

    /**
     * Crea una excepción ResourceNotFoundException para un webhook no encontrado
     *
     * @param webhookName Nombre del webhook no encontrado
     * @return Instancia de ResourceNotFoundException
     */
    public static ResourceNotFoundException webhookNotFound(String webhookName) {
        return new ResourceNotFoundException(
                "Webhook no encontrado: " + webhookName,
                "webhook",
                webhookName
        );
    }
}