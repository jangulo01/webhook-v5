package com.sg.webhookservice.exception;

import org.springframework.http.HttpStatus;

/**
 * Excepción lanzada cuando se proporcionan credenciales inválidas en un intento de autenticación.
 * 
 * Esta excepción se utiliza en casos como:
 * - Nombre de usuario o contraseña incorrectos
 * - Tokens JWT inválidos o expirados
 * - Claves API incorrectas
 * - Firmas HMAC inválidas
 */
public class BadCredentialsException extends ApiException {
    
    /**
     * Código de error estándar para esta excepción
     */
    private static final String ERROR_CODE = "INVALID_CREDENTIALS";
    
    /**
     * Estado HTTP predeterminado para esta excepción
     */
    private static final HttpStatus DEFAULT_STATUS = HttpStatus.UNAUTHORIZED;
    
    /**
     * Constructor básico con mensaje de error.
     * 
     * @param message Mensaje descriptivo del error
     */
    public BadCredentialsException(String message) {
        super(message, DEFAULT_STATUS, ERROR_CODE);
    }
    
    /**
     * Constructor con mensaje de error e información adicional.
     * 
     * @param message Mensaje descriptivo del error
     * @param additionalInfo Información adicional sobre el error
     */
    public BadCredentialsException(String message, String additionalInfo) {
        super(message, DEFAULT_STATUS, ERROR_CODE, additionalInfo);
    }
    
    /**
     * Constructor con mensaje de error y causa subyacente.
     * 
     * @param message Mensaje descriptivo del error
     * @param cause Excepción que causó este error
     */
    public BadCredentialsException(String message, Throwable cause) {
        super(message, cause, DEFAULT_STATUS, ERROR_CODE);
    }
    
    /**
     * Constructor completo con todos los parámetros.
     * 
     * @param message Mensaje descriptivo del error
     * @param cause Excepción que causó este error
     * @param additionalInfo Información adicional sobre el error
     */
    public BadCredentialsException(String message, Throwable cause, String additionalInfo) {
        super(message, cause, DEFAULT_STATUS, ERROR_CODE, additionalInfo);
    }
    
    /**
     * Constructor para especificar un origen de autenticación.
     * 
     * @param message Mensaje descriptivo del error
     * @param authSource Origen de la autenticación (e.g., "JWT", "Basic", "ApiKey")
     */
    public BadCredentialsException(String message, String authSource, int failedAttempts) {
        super(
            message,
            DEFAULT_STATUS,
            ERROR_CODE,
            String.format("Authentication source: %s, Failed attempts: %d", authSource, failedAttempts)
        );
    }
    
    /**
     * Genera una instancia para credenciales inválidas sin detalles específicos.
     * 
     * @return Instancia de BadCredentialsException
     */
    public static BadCredentialsException invalidCredentials() {
        return new BadCredentialsException("Invalid authentication credentials");
    }
    
    /**
     * Genera una instancia para token JWT inválido o expirado.
     * 
     * @param details Detalles específicos del problema con el token
     * @return Instancia de BadCredentialsException
     */
    public static BadCredentialsException invalidToken(String details) {
        return new BadCredentialsException(
            "Invalid or expired token",
            "JWT",
            details
        );
    }
    
    /**
     * Genera una instancia para credenciales básicas inválidas.
     * 
     * @param username Nombre de usuario que intentó autenticarse
     * @return Instancia de BadCredentialsException
     */
    public static BadCredentialsException invalidBasicCredentials(String username) {
        return new BadCredentialsException(
            "Invalid username or password",
            String.format("Username: %s", username)
        );
    }
    
    /**
     * Genera una instancia para clave API inválida.
     * 
     * @param apiKeyId Identificador de la clave API
     * @return Instancia de BadCredentialsException
     */
    public static BadCredentialsException invalidApiKey(String apiKeyId) {
        return new BadCredentialsException(
            "Invalid API key",
            String.format("API Key ID: %s", apiKeyId)
        );
    }
    
    /**
     * Genera una instancia para firma HMAC inválida.
     * 
     * @param webhookName Nombre del webhook con firma inválida
     * @return Instancia de BadCredentialsException
     */
    public static BadCredentialsException invalidHmacSignature(String webhookName) {
        return new BadCredentialsException(
            "Invalid HMAC signature",
            String.format("Webhook: %s", webhookName)
        );
    }
}