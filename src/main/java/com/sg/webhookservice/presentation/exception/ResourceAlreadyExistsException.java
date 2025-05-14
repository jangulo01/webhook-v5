package com.sg.webhookservice.presentation.exception;

/**
 * Excepción lanzada cuando se intenta crear un recurso que ya existe.
 * Esta excepción se utiliza principalmente para identificar conflictos
 * al intentar crear recursos con identificadores únicos ya utilizados.
 */
public class ResourceAlreadyExistsException extends RuntimeException {

    /**
     * Tipo de recurso que tiene el conflicto.
     */
    private final String resourceType;

    /**
     * Identificador del recurso en conflicto.
     */
    private final String resourceId;

    /**
     * Constructor con mensaje de error.
     *
     * @param message Mensaje detallado del error
     */
    public ResourceAlreadyExistsException(String message) {
        super(message);
        this.resourceType = null;
        this.resourceId = null;
    }

    /**
     * Constructor con mensaje de error y tipo de recurso.
     *
     * @param message Mensaje detallado del error
     * @param resourceType Tipo de recurso en conflicto (ej. "webhook", "user")
     */
    public ResourceAlreadyExistsException(String message, String resourceType) {
        super(message);
        this.resourceType = resourceType;
        this.resourceId = null;
    }

    /**
     * Constructor completo con mensaje, tipo de recurso e identificador.
     *
     * @param message Mensaje detallado del error
     * @param resourceType Tipo de recurso en conflicto (ej. "webhook", "user")
     * @param resourceId Identificador del recurso en conflicto
     */
    public ResourceAlreadyExistsException(String message, String resourceType, String resourceId) {
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
    public ResourceAlreadyExistsException(String message, Throwable cause) {
        super(message, cause);
        this.resourceType = null;
        this.resourceId = null;
    }

    /**
     * Constructor completo con causa.
     *
     * @param message Mensaje detallado del error
     * @param cause Excepción original que causó este error
     * @param resourceType Tipo de recurso en conflicto
     * @param resourceId Identificador del recurso en conflicto
     */
    public ResourceAlreadyExistsException(String message, Throwable cause, String resourceType, String resourceId) {
        super(message, cause);
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }

    /**
     * Obtiene el tipo de recurso en conflicto.
     *
     * @return Tipo de recurso o null si no se especificó
     */
    public String getResourceType() {
        return resourceType;
    }

    /**
     * Obtiene el identificador del recurso en conflicto.
     *
     * @return Identificador del recurso o null si no se especificó
     */
    public String getResourceId() {
        return resourceId;
    }

    /**
     * Crea una excepción para un webhook que ya existe con el nombre dado.
     *
     * @param webhookName Nombre del webhook en conflicto
     * @return Nueva excepción ResourceAlreadyExistsException
     */
    public static ResourceAlreadyExistsException forWebhook(String webhookName) {
        return new ResourceAlreadyExistsException(
                "Ya existe un webhook con el nombre '" + webhookName + "'",
                "webhook",
                webhookName
        );
    }
}