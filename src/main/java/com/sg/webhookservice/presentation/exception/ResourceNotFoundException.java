package com.sg.webhookservice.presentation.exception;
/**
 * Excepción lanzada cuando un recurso solicitado no puede ser encontrado.
 * Esta excepción se utiliza para indicar que un recurso identificado por
 * un ID, nombre u otro identificador no existe en el sistema.
 */
public class ResourceNotFoundException extends RuntimeException {

    /**
     * Tipo de recurso que no se encontró.
     */
    private final String resourceType;

    /**
     * Identificador del recurso no encontrado.
     */
    private final String resourceId;

    /**
     * Constructor con mensaje de error.
     *
     * @param message Mensaje detallado del error
     */
    public ResourceNotFoundException(String message) {
        super(message);
        this.resourceType = null;
        this.resourceId = null;
    }

    /**
     * Constructor con mensaje de error y tipo de recurso.
     *
     * @param message Mensaje detallado del error
     * @param resourceType Tipo de recurso no encontrado (ej. "webhook", "message")
     */
    public ResourceNotFoundException(String message, String resourceType) {
        super(message);
        this.resourceType = resourceType;
        this.resourceId = null;
    }

    /**
     * Constructor completo con mensaje, tipo de recurso e identificador.
     *
     * @param message Mensaje detallado del error
     * @param resourceType Tipo de recurso no encontrado (ej. "webhook", "message")
     * @param resourceId Identificador del recurso no encontrado
     */
    public ResourceNotFoundException(String message, String resourceType, String resourceId) {
        super(message);
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }

    /**
     * Constructor con causa de la excepción.
     *
     * @param message Mensaje detallado del error
     * @param cause Excepción original que causó este error
     */
    public ResourceNotFoundException(String message, Throwable cause) {
        super(message, cause);
        this.resourceType = null;
        this.resourceId = null;
    }

    /**
     * Constructor completo con causa.
     *
     * @param message Mensaje detallado del error
     * @param cause Excepción original que causó este error
     * @param resourceType Tipo de recurso no encontrado
     * @param resourceId Identificador del recurso no encontrado
     */
    public ResourceNotFoundException(String message, Throwable cause, String resourceType, String resourceId) {
        super(message, cause);
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }

    /**
     * Obtiene el tipo de recurso no encontrado.
     *
     * @return Tipo de recurso o null si no se especificó
     */
    public String getResourceType() {
        return resourceType;
    }

    /**
     * Obtiene el identificador del recurso no encontrado.
     *
     * @return Identificador del recurso o null si no se especificó
     */
    public String getResourceId() {
        return resourceId;
    }

    /**
     * Crea una excepción para un webhook no encontrado con el ID dado.
     *
     * @param webhookId ID del webhook no encontrado
     * @return Nueva excepción ResourceNotFoundException
     */
    public static ResourceNotFoundException webhookNotFound(String webhookId) {
        return new ResourceNotFoundException(
                "No se encontró el webhook con ID: " + webhookId,
                "webhook",
                webhookId
        );
    }

    /**
     * Crea una excepción para un webhook no encontrado con el nombre dado.
     *
     * @param webhookName Nombre del webhook no encontrado
     * @return Nueva excepción ResourceNotFoundException
     */
    public static ResourceNotFoundException webhookNotFoundByName(String webhookName) {
        return new ResourceNotFoundException(
                "No se encontró el webhook con nombre: " + webhookName,
                "webhook",
                webhookName
        );
    }

    /**
     * Crea una excepción para un mensaje no encontrado con el ID dado.
     *
     * @param messageId ID del mensaje no encontrado
     * @return Nueva excepción ResourceNotFoundException
     */
    public static ResourceNotFoundException messageNotFound(String messageId) {
        return new ResourceNotFoundException(
                "No se encontró el mensaje con ID: " + messageId,
                "message",
                messageId
        );
    }
}
