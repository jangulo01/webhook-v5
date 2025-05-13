package com.sg.webhookservice.exception;

import org.springframework.http.HttpStatus;

/**
 * Excepción lanzada cuando ocurre un problema de seguridad que no está
 * directamente relacionado con autenticación o autorización.
 * 
 * Esta excepción se utiliza en casos como:
 * - Problemas de cifrado o descifrado
 * - Tokens inválidos o manipulados
 * - Infracciones de seguridad como CSRF
 * - Problemas de gestión de claves
 * - Configuraciones de seguridad inválidas
 */
public class SecurityException extends ApiException {
    
    /**
     * Código de error estándar para esta excepción
     */
    private static final String ERROR_CODE = "SECURITY_ERROR";
    
    /**
     * Estado HTTP predeterminado para esta excepción
     */
    private static final HttpStatus DEFAULT_STATUS = HttpStatus.FORBIDDEN;
    
    /**
     * Categoría específica del error de seguridad
     */
    private final SecurityCategory category;
    
    /**
     * Constructor básico con mensaje de error.
     * 
     * @param message Mensaje descriptivo del error
     */
    public SecurityException(String message) {
        super(message, DEFAULT_STATUS, ERROR_CODE);
        this.category = SecurityCategory.GENERAL;
    }
    
    /**
     * Constructor con mensaje y categoría de seguridad.
     * 
     * @param message Mensaje descriptivo del error
     * @param category Categoría de seguridad del error
     */
    public SecurityException(String message, SecurityCategory category) {
        super(message, DEFAULT_STATUS, ERROR_CODE);
        this.category = category;
    }
    
    /**
     * Constructor con mensaje, categoría e información adicional.
     * 
     * @param message Mensaje descriptivo del error
     * @param category Categoría de seguridad del error
     * @param additionalInfo Información adicional sobre el error
     */
    public SecurityException(String message, SecurityCategory category, String additionalInfo) {
        super(message, DEFAULT_STATUS, ERROR_CODE, additionalInfo);
        this.category = category;
    }
    
    /**
     * Constructor para encapsular otra excepción.
     * 
     * @param message Mensaje descriptivo del error
     * @param cause Excepción original
     * @param category Categoría de seguridad del error
     */
    public SecurityException(String message, Throwable cause, SecurityCategory category) {
        super(message, cause, DEFAULT_STATUS, ERROR_CODE);
        this.category = category;
    }
    
    /**
     * Constructor completo con todos los parámetros.
     * 
     * @param message Mensaje descriptivo del error
     * @param cause Excepción original
     * @param category Categoría de seguridad del error
     * @param additionalInfo Información adicional sobre el error
     */
    public SecurityException(String message, Throwable cause, 
                           SecurityCategory category, String additionalInfo) {
        super(message, cause, DEFAULT_STATUS, ERROR_CODE, additionalInfo);
        this.category = category;
    }
    
    /**
     * Constructor que permite especificar un estado HTTP personalizado.
     * 
     * @param message Mensaje descriptivo del error
     * @param status Estado HTTP personalizado
     * @param category Categoría de seguridad del error
     */
    public SecurityException(String message, HttpStatus status, SecurityCategory category) {
        super(message, status, ERROR_CODE);
        this.category = category;
    }
    
    /**
     * Obtiene la categoría de seguridad del error.
     * 
     * @return Categoría de seguridad
     */
    public SecurityCategory getCategory() {
        return category;
    }
    
    /**
     * Genera una instancia para errores de CSRF.
     * 
     * @param requestOrigin Origen de la solicitud
     * @return Instancia de SecurityException
     */
    public static SecurityException csrfViolation(String requestOrigin) {
        return new SecurityException(
            "Violación de seguridad CSRF detectada",
            SecurityCategory.CSRF,
            "Origen de la solicitud: " + requestOrigin
        );
    }
    
    /**
     * Genera una instancia para errores de token.
     * 
     * @param tokenType Tipo de token (JWT, API key, etc.)
     * @param issue Problema específico con el token
     * @return Instancia de SecurityException
     */
    public static SecurityException invalidToken(String tokenType, String issue) {
        return new SecurityException(
            String.format("Token %s inválido: %s", tokenType, issue),
            SecurityCategory.TOKEN,
            "Token manipulation or forgery detected"
        );
    }
    
    /**
     * Genera una instancia para errores de cifrado.
     * 
     * @param operation Operación criptográfica que falló
     * @param cause Excepción original
     * @return Instancia de SecurityException
     */
    public static SecurityException encryptionError(String operation, Throwable cause) {
        return new SecurityException(
            "Error durante operación criptográfica",
            cause,
            SecurityCategory.ENCRYPTION,
            "Operación: " + operation
        );
    }
    
    /**
     * Genera una instancia para errores de integridad de datos sensibles.
     * 
     * @param dataType Tipo de datos afectados
     * @return Instancia de SecurityException
     */
    public static SecurityException dataIntegrityViolation(String dataType) {
        return new SecurityException(
            "Posible manipulación de datos sensibles detectada",
            SecurityCategory.DATA_INTEGRITY,
            "Tipo de datos: " + dataType
        );
    }
    
    /**
     * Genera una instancia para errores de configuración de seguridad.
     * 
     * @param component Componente mal configurado
     * @param issue Problema específico
     * @return Instancia de SecurityException
     */
    public static SecurityException securityMisconfiguration(String component, String issue) {
        return new SecurityException(
            String.format("Configuración de seguridad incorrecta en %s: %s", component, issue),
            SecurityCategory.CONFIGURATION,
            "Es necesario revisar la configuración del componente"
        );
    }
    
    /**
     * Genera una instancia para límites de tasa excedidos (rate limiting).
     * 
     * @param clientIp IP del cliente
     * @param endpoint Endpoint afectado
     * @param currentRate Tasa actual
     * @param limit Límite permitido
     * @return Instancia de SecurityException
     */
    public static SecurityException rateLimitExceeded(
            String clientIp, String endpoint, int currentRate, int limit) {
        return new SecurityException(
            "Límite de tasa excedido",
            HttpStatus.TOO_MANY_REQUESTS, // 429 Too Many Requests
            SecurityCategory.RATE_LIMITING,
            String.format("IP: %s, Endpoint: %s, Tasa: %d, Límite: %d", 
                          clientIp, endpoint, currentRate, limit)
        );
    }
    
    /**
     * Genera una instancia para posibles ataques detectados.
     * 
     * @param attackType Tipo de ataque detectado
     * @param evidence Evidencia del posible ataque
     * @return Instancia de SecurityException
     */
    public static SecurityException potentialAttack(String attackType, String evidence) {
        return new SecurityException(
            "Posible ataque de seguridad detectado: " + attackType,
            SecurityCategory.ATTACK,
            "Evidencia: " + evidence
        );
    }
    
    /**
     * Categorías de errores de seguridad.
     */
    public enum SecurityCategory {
        /**
         * Problemas con Cross-Site Request Forgery
         */
        CSRF,
        
        /**
         * Problemas con tokens (JWT, API keys, etc.)
         */
        TOKEN,
        
        /**
         * Problemas con operaciones criptográficas
         */
        ENCRYPTION,
        
        /**
         * Problemas de integridad de datos
         */
        DATA_INTEGRITY,
        
        /**
         * Configuración incorrecta de seguridad
         */
        CONFIGURATION,
        
        /**
         * Problemas de limitación de tasa (rate limiting)
         */
        RATE_LIMITING,
        
        /**
         * Posibles ataques detectados
         */
        ATTACK,
        
        /**
         * Problemas de gestión de sesiones
         */
        SESSION,
        
        /**
         * Problemas generales de seguridad
         */
        GENERAL
    }
}