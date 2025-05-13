package com.sg.webhookservice.exception;

import org.springframework.http.HttpStatus;

/**
 * Excepción lanzada cuando un recurso solicitado no puede ser encontrado.
 * 
 * Esta excepción se utiliza en casos como:
 * - Intentar obtener una configuración de webhook que no existe
 * - Intentar acceder a un mensaje por un ID inexistente
 * - Intentar actualizar o eliminar un recurso que no se encuentra en el sistema
 */
public class ResourceNotFoundException extends ApiException {
    
    /**
     * Código de error estándar para esta excepción
     */
    private static final String ERROR_CODE = "RESOURCE_NOT_FOUND";
    
    /**
     * Estado HTTP predeterminado para esta excepción - 404 Not Found
     */
    private static final HttpStatus DEFAULT_STATUS = HttpStatus.NOT_FOUND;
    
    /**
     * Tipo de recurso que no se pudo encontrar
     */
    private final String resourceType;
    
    /**
     * Identificador del recurso que se buscaba
     */
    private final String resourceId;
    
    /**
     * Constructor básico con mensaje de error.
     * 
     * @param message Mensaje descriptivo del error
     */
    public ResourceNotFoundException(String message) {
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
    public ResourceNotFoundException(String message, String additionalInfo) {
        super(message, DEFAULT_STATUS, ERROR_CODE, additionalInfo);
        this.resourceType = null;
        this.resourceId = null;
    }
    
    /**
     * Constructor con tipo de recurso e identificador.
     * 
     * @param resourceType Tipo de recurso (e.g., "webhook", "message", "user")
     * @param resourceId Identificador del recurso buscado
     */
    public ResourceNotFoundException(String resourceType, String resourceId) {
        super(
            String.format("No se encontró el recurso %s con identificador '%s'", resourceType, resourceId),
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
     * @param resourceId Identificador del recurso buscado
     * @param additionalInfo Información adicional sobre el error
     */
    public ResourceNotFoundException(String resourceType, String resourceId, String additionalInfo) {
        super(
            String.format("No se encontró el recurso %s con identificador '%s'", resourceType, resourceId),
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
     * @param resourceId Identificador del recurso buscado
     */
    public ResourceNotFoundException(String message, String resourceType, String resourceId) {
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
     * @param resourceId Identificador del recurso buscado
     */
    public ResourceNotFoundException(
            String message, Throwable cause, String resourceType, String resourceId) {
        super(message, cause, DEFAULT_STATUS, ERROR_CODE);
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }
    
    /**
     * Obtiene el tipo de recurso que no se pudo encontrar.
     * 
     * @return Tipo de recurso o null si no está disponible
     */
    public String getResourceType() {
        return resourceType;
    }
    
    /**
     * Obtiene el identificador del recurso que se buscaba.
     * 
     * @return Identificador del recurso o null si no está disponible
     */
    public String getResourceId() {
        return resourceId;
    }
    
    /**
     * Genera una instancia para configuración de webhook no encontrada.
     * 
     * @param webhookName Nombre del webhook buscado
     * @return Instancia de ResourceNotFoundException
     */
    public static ResourceNotFoundException webhookConfigNotFound(String webhookName) {
        return new ResourceNotFoundException(
            "webhook",
            webhookName,
            "La configuración de webhook solicitada no existe o ha sido eliminada. " +
            "Verifique el nombre del webhook e intente nuevamente."
        );
    }
    
    /**
     * Genera una instancia para mensaje de webhook no encontrado.
     * 
     * @param messageId ID del mensaje buscado
     * @return Instancia de ResourceNotFoundException
     */
    public static ResourceNotFoundException messageNotFound(String messageId) {
        return new ResourceNotFoundException(
            "message",
            messageId,
            "El mensaje de webhook solicitado no existe, ha sido eliminado o el ID es inválido."
        );
    }
    
    /**
     * Genera una instancia para clave API no encontrada.
     * 
     * @param keyId ID o nombre de la clave API
     * @return Instancia de ResourceNotFoundException
     */
    public static ResourceNotFoundException apiKeyNotFound(String keyId) {
        return new ResourceNotFoundException(
            "api-key",
            keyId,
            "La clave API especificada no existe o ha sido revocada."
        );
    }
    
    /**
     * Genera una instancia para usuario no encontrado.
     * 
     * @param userId ID o nombre de usuario
     * @return Instancia de ResourceNotFoundException
     */
    public static ResourceNotFoundException userNotFound(String userId) {
        return new ResourceNotFoundException(
            "user",
            userId,
            "El usuario especificado no existe o ha sido desactivado."
        );
    }
    
    /**
     * Genera una instancia para búsqueda por campo que no produjo resultados.
     * 
     * @param resourceType Tipo de recurso
     * @param fieldName Nombre del campo utilizado para la búsqueda
     * @param fieldValue Valor del campo buscado
     * @return Instancia de ResourceNotFoundException
     */
    public static ResourceNotFoundException notFoundByField(
            String resourceType, String fieldName, String fieldValue) {
        return new ResourceNotFoundException(
            String.format("No se encontró ningún %s con %s igual a '%s'", 
                    resourceType, fieldName, fieldValue),
            resourceType,
            fieldValue
        );
    }
    
    /**
     * Genera una instancia para recurso eliminado.
     * 
     * @param resourceType Tipo de recurso
     * @param resourceId Identificador del recurso
     * @return Instancia de ResourceNotFoundException
     */
    public static ResourceNotFoundException resourceDeleted(String resourceType, String resourceId) {
        return new ResourceNotFoundException(
            String.format("El %s con identificador '%s' ha sido eliminado previamente", 
                    resourceType, resourceId),
            resourceType,
            resourceId,
            "El recurso solicitado existió anteriormente pero ya no está disponible."
        );
    }
    
    /**
     * Genera una instancia para recurso inaccesible por permisos.
     * 
     * @param resourceType Tipo de recurso
     * @param resourceId Identificador del recurso
     * @return Instancia de ResourceNotFoundException
     */
    public static ResourceNotFoundException resourceNotAccessible(String resourceType, String resourceId) {
        return new ResourceNotFoundException(
            String.format("No se puede acceder al %s con identificador '%s'", 
                    resourceType, resourceId),
            resourceType,
            resourceId,
            "El recurso solicitado puede existir pero no está accesible con sus permisos actuales."
        );
    }
    
    /**
     * Genera una instancia desde una excepción NoResultException de JPA.
     * 
     * @param resourceType Tipo de recurso
     * @param resourceId Identificador del recurso
     * @param cause Excepción original
     * @return Instancia de ResourceNotFoundException
     */
    public static ResourceNotFoundException fromNoResultException(
            String resourceType, String resourceId, Throwable cause) {
        return new ResourceNotFoundException(
            String.format("No se encontró el recurso %s con identificador '%s'", 
                    resourceType, resourceId),
            cause,
            resourceType,
            resourceId
        );
    }
}