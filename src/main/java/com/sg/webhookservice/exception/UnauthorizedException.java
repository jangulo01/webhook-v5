package com.sg.webhookservice.exception;

import org.springframework.http.HttpStatus;

/**
 * Excepción lanzada cuando un usuario autenticado intenta acceder a un recurso
 * o realizar una operación para la que no tiene los permisos adecuados.
 * 
 * Esta excepción se diferencia de BadCredentialsException en que el usuario 
 * está correctamente autenticado, pero no tiene autorización para la acción solicitada.
 * 
 * Se utiliza en casos como:
 * - Intentar acceder a un recurso que pertenece a otro usuario
 * - Intentar realizar una operación administrativa sin tener rol de administrador
 * - Intentar modificar datos para los que solo se tiene permiso de lectura
 */
public class UnauthorizedException extends ApiException {
    
    /**
     * Código de error estándar para esta excepción
     */
    private static final String ERROR_CODE = "UNAUTHORIZED_ACCESS";
    
    /**
     * Estado HTTP predeterminado para esta excepción - 403 Forbidden
     */
    private static final HttpStatus DEFAULT_STATUS = HttpStatus.FORBIDDEN;
    
    /**
     * Recurso al que se intentó acceder sin autorización
     */
    private final String resource;
    
    /**
     * Operación que se intentó realizar sin autorización
     */
    private final String operation;
    
    /**
     * Roles o permisos requeridos para la operación
     */
    private final String requiredPermissions;
    
    /**
     * Constructor básico con mensaje de error.
     * 
     * @param message Mensaje descriptivo del error
     */
    public UnauthorizedException(String message) {
        super(message, DEFAULT_STATUS, ERROR_CODE);
        this.resource = null;
        this.operation = null;
        this.requiredPermissions = null;
    }
    
    /**
     * Constructor con mensaje de error e información adicional.
     * 
     * @param message Mensaje descriptivo del error
     * @param additionalInfo Información adicional sobre el error
     */
    public UnauthorizedException(String message, String additionalInfo) {
        super(message, DEFAULT_STATUS, ERROR_CODE, additionalInfo);
        this.resource = null;
        this.operation = null;
        this.requiredPermissions = null;
    }
    
    /**
     * Constructor con información específica de autorización.
     * 
     * @param message Mensaje descriptivo del error
     * @param resource Recurso al que se intentó acceder
     * @param operation Operación que se intentó realizar
     * @param requiredPermissions Permisos requeridos
     */
    public UnauthorizedException(String message, String resource, 
                               String operation, String requiredPermissions) {
        super(message, DEFAULT_STATUS, ERROR_CODE);
        this.resource = resource;
        this.operation = operation;
        this.requiredPermissions = requiredPermissions;
    }
    
    /**
     * Constructor completo con toda la información.
     * 
     * @param message Mensaje descriptivo del error
     * @param resource Recurso al que se intentó acceder
     * @param operation Operación que se intentó realizar
     * @param requiredPermissions Permisos requeridos
     * @param additionalInfo Información adicional sobre el error
     */
    public UnauthorizedException(String message, String resource, 
                               String operation, String requiredPermissions,
                               String additionalInfo) {
        super(message, DEFAULT_STATUS, ERROR_CODE, additionalInfo);
        this.resource = resource;
        this.operation = operation;
        this.requiredPermissions = requiredPermissions;
    }
    
    /**
     * Obtiene el recurso al que se intentó acceder.
     * 
     * @return Recurso o null si no está disponible
     */
    public String getResource() {
        return resource;
    }
    
    /**
     * Obtiene la operación que se intentó realizar.
     * 
     * @return Operación o null si no está disponible
     */
    public String getOperation() {
        return operation;
    }
    
    /**
     * Obtiene los permisos requeridos para la operación.
     * 
     * @return Permisos requeridos o null si no está disponible
     */
    public String getRequiredPermissions() {
        return requiredPermissions;
    }
    
    /**
     * Genera una instancia para acceso denegado a recurso.
     * 
     * @param resourceType Tipo de recurso
     * @param resourceId Identificador del recurso
     * @return Instancia de UnauthorizedException
     */
    public static UnauthorizedException accessDenied(String resourceType, String resourceId) {
        return new UnauthorizedException(
            String.format("Acceso denegado a %s con ID '%s'", resourceType, resourceId),
            resourceType,
            "READ",
            "Requiere permisos de lectura para este recurso"
        );
    }
    
    /**
     * Genera una instancia para modificación denegada de recurso.
     * 
     * @param resourceType Tipo de recurso
     * @param resourceId Identificador del recurso
     * @return Instancia de UnauthorizedException
     */
    public static UnauthorizedException modificationDenied(String resourceType, String resourceId) {
        return new UnauthorizedException(
            String.format("No está autorizado para modificar %s con ID '%s'", resourceType, resourceId),
            resourceType,
            "MODIFY",
            "Requiere permisos de escritura para este recurso"
        );
    }
    
    /**
     * Genera una instancia para operación administrativa denegada.
     * 
     * @param operation Operación administrativa
     * @return Instancia de UnauthorizedException
     */
    public static UnauthorizedException adminOperationDenied(String operation) {
        return new UnauthorizedException(
            String.format("No está autorizado para realizar la operación administrativa '%s'", operation),
            "ADMIN",
            operation,
            "Requiere rol de ADMINISTRADOR"
        );
    }
    
    /**
     * Genera una instancia para operación denegada por falta de rol.
     * 
     * @param operation Operación solicitada
     * @param requiredRole Rol requerido
     * @return Instancia de UnauthorizedException
     */
    public static UnauthorizedException roleRequired(String operation, String requiredRole) {
        return new UnauthorizedException(
            String.format("No tiene el rol necesario para realizar '%s'", operation),
            "SYSTEM",
            operation,
            String.format("Requiere rol: %s", requiredRole)
        );
    }
    
    /**
     * Genera una instancia para recurso que pertenece a otro usuario.
     * 
     * @param resourceType Tipo de recurso
     * @param resourceId Identificador del recurso
     * @param ownerId Propietario del recurso
     * @return Instancia de UnauthorizedException
     */
    public static UnauthorizedException resourceBelongsToAnotherUser(
            String resourceType, String resourceId, String ownerId) {
        return new UnauthorizedException(
            String.format("No autorizado para acceder a %s con ID '%s'", resourceType, resourceId),
            resourceType,
            "ACCESS",
            "Solo el propietario puede acceder a este recurso",
            String.format("Propietario: %s", ownerId)
        );
    }
    
    /**
     * Genera una instancia para permisos insuficientes en webhook.
     * 
     * @param webhookName Nombre del webhook
     * @param operation Operación solicitada
     * @return Instancia de UnauthorizedException
     */
    public static UnauthorizedException insufficientWebhookPermissions(
            String webhookName, String operation) {
        return new UnauthorizedException(
            String.format("Permisos insuficientes para %s en webhook '%s'", operation, webhookName),
            "WEBHOOK",
            operation,
            "Requiere permisos específicos para este webhook"
        );
    }
    
    /**
     * Genera una instancia desde Spring Security AccessDeniedException.
     * 
     * @param original Excepción original
     * @return Instancia de UnauthorizedException
     */
    public static UnauthorizedException fromAccessDeniedException(
            org.springframework.security.access.AccessDeniedException original) {
        return new UnauthorizedException(
            "Acceso denegado a recurso protegido",
            original.getMessage()
        );
    }
}