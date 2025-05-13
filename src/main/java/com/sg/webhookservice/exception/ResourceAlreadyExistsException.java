package com.sg.webhookservice.exception;

import org.springframework.http.HttpStatus;

/**
 * Excepción lanzada cuando se intenta crear un recurso que ya existe.
 * 
 * Esta excepción se utiliza en casos como:
 * - Intentar crear una configuración de webhook con un nombre que ya está en uso
 * - Intentar registrar un usuario con un nombre de usuario o email ya existente
 * - Intentar crear un recurso con un identificador único que ya está asignado
 */
public class ResourceAlreadyExistsException extends ApiException {
    
    /**
     * Código de error estándar para esta excepción
     */
    private static final String ERROR_CODE = "RESOURCE_ALREADY_EXISTS";
    
    /**
     * Estado HTTP predeterminado para esta excepción - 409 Conflict
     */
    private static final HttpStatus DEFAULT_STATUS = HttpStatus.CONFLICT;
    
    /**
     * Tipo de recurso que causó el conflicto
     */
    private final String resourceType;
    
    /**
     * Identificador del recurso duplicado
     */
    private final String resourceId;
    
    /**
     * Constructor básico con mensaje de error.
     * 
     * @param message Mensaje descriptivo del error
     */
    public ResourceAlreadyExistsException(String message) {
        super(message, DEFAULT_STATUS, ERROR_CODE);
        this.resourceType = null;
        this.resourceId = null;
    }
    
    /**
     * Constructor con mensaje de error e información adicional.
     * 
     * @param message Mensaje descriptivo del error
     * @param additionalInfo Información adicional sobre el error
     */
    public ResourceAlreadyExistsException(String message, String additionalInfo) {
        super(message, DEFAULT_STATUS, ERROR_CODE, additionalInfo);
        this.resourceType = null;
        this.resourceId = null;
    }
    
    /**
     * Constructor con tipo de recurso e identificador.
     * 
     * @param resourceType Tipo de recurso (e.g., "webhook", "user", "api-key")
     * @param resourceId Identificador del recurso (e.g., nombre, ID, email)
     */
    public ResourceAlreadyExistsException(String resourceType, String resourceId) {
        super(
            String.format("El recurso %s con identificador '%s' ya existe", resourceType, resourceId),
            DEFAULT_STATUS,
            ERROR_CODE
        );
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }
    
    /**
     * Constructor con tipo de recurso, identificador e información adicional.
     * 
     * @param resourceType Tipo de recurso
     * @param resourceId Identificador del recurso
     * @param additionalInfo Información adicional sobre el error
     */
    public ResourceAlreadyExistsException(String resourceType, String resourceId, String additionalInfo) {
        super(
            String.format("El recurso %s con identificador '%s' ya existe", resourceType, resourceId),
            DEFAULT_STATUS,
            ERROR_CODE,
            additionalInfo
        );
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }
    
    /**
     * Constructor con mensaje, tipo de recurso e identificador.
     * 
     * @param message Mensaje descriptivo del error
     * @param resourceType Tipo de recurso
     * @param resourceId Identificador del recurso
     */
    public ResourceAlreadyExistsException(String message, String resourceType, String resourceId) {
        super(message, DEFAULT_STATUS, ERROR_CODE);
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }
    
    /**
     * Constructor con mensaje, causa, tipo de recurso e identificador.
     * 
     * @param message Mensaje descriptivo del error
     * @param cause Excepción que causó este error
     * @param resourceType Tipo de recurso
     * @param resourceId Identificador del recurso
     */
    public ResourceAlreadyExistsException(
            String message, Throwable cause, String resourceType, String resourceId) {
        super(message, cause, DEFAULT_STATUS, ERROR_CODE);
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }
    
    /**
     * Obtiene el tipo de recurso que causó el conflicto.
     * 
     * @return Tipo de recurso o null si no está disponible
     */
    public String getResourceType() {
        return resourceType;
    }
    
    /**
     * Obtiene el identificador del recurso duplicado.
     * 
     * @return Identificador del recurso o null si no está disponible
     */
    public String getResourceId() {
        return resourceId;
    }
    
    /**
     * Genera una instancia para configuración de webhook duplicada.
     * 
     * @param webhookName Nombre del webhook duplicado
     * @return Instancia de ResourceAlreadyExistsException
     */
    public static ResourceAlreadyExistsException duplicateWebhookConfig(String webhookName) {
        return new ResourceAlreadyExistsException(
            "webhook",
            webhookName,
            "Ya existe una configuración de webhook con este nombre. " +
            "Utilice un nombre diferente o actualice la configuración existente."
        );
    }
    
    /**
     * Genera una instancia para campo único duplicado.
     * 
     * @param resourceType Tipo de recurso
     * @param fieldName Nombre del campo único
     * @param fieldValue Valor del campo único
     * @return Instancia de ResourceAlreadyExistsException
     */
    public static ResourceAlreadyExistsException duplicateUniqueField(
            String resourceType, String fieldName, String fieldValue) {
        return new ResourceAlreadyExistsException(
            String.format("Ya existe un %s con %s '%s'", resourceType, fieldName, fieldValue),
            resourceType,
            fieldValue
        );
    }
    
    /**
     * Genera una instancia para API key duplicada.
     * 
     * @param keyName Nombre o alias de la clave API
     * @return Instancia de ResourceAlreadyExistsException
     */
    public static ResourceAlreadyExistsException duplicateApiKey(String keyName) {
        return new ResourceAlreadyExistsException(
            "Ya existe una clave API con este nombre",
            "api-key",
            keyName
        );
    }
    
    /**
     * Genera una instancia desde una excepción de integridad de datos.
     * 
     * @param resourceType Tipo de recurso
     * @param resourceId Identificador del recurso
     * @param cause Excepción original
     * @return Instancia de ResourceAlreadyExistsException
     */
    public static ResourceAlreadyExistsException fromDataIntegrityViolation(
            String resourceType, String resourceId, Throwable cause) {
        return new ResourceAlreadyExistsException(
            String.format("El recurso %s con identificador '%s' ya existe", resourceType, resourceId),
            cause,
            resourceType,
            resourceId
        );
    }
}