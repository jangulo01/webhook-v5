package com.sg.webhookservice.exception;

import org.springframework.http.HttpStatus;

/**
 * Excepción lanzada cuando una firma HMAC no puede ser verificada correctamente.
 * 
 * Esta excepción se utiliza en casos como:
 * - Firmas HMAC que no coinciden con el payload
 * - Cabeceras de firma ausentes o mal formadas
 * - Problemas con el algoritmo de firma
 * - Secretos inválidos o no encontrados
 */
public class InvalidSignatureException extends ApiException {
    
    /**
     * Código de error estándar para esta excepción
     */
    private static final String ERROR_CODE = "INVALID_SIGNATURE";
    
    /**
     * Estado HTTP predeterminado para esta excepción
     */
    private static final HttpStatus DEFAULT_STATUS = HttpStatus.UNAUTHORIZED;
    
    /**
     * Constructor básico con mensaje de error.
     * 
     * @param message Mensaje descriptivo del error
     */
    public InvalidSignatureException(String message) {
        super(message, DEFAULT_STATUS, ERROR_CODE);
    }
    
    /**
     * Constructor con mensaje de error e información adicional.
     * 
     * @param message Mensaje descriptivo del error
     * @param additionalInfo Información adicional sobre el error
     */
    public InvalidSignatureException(String message, String additionalInfo) {
        super(message, DEFAULT_STATUS, ERROR_CODE, additionalInfo);
    }
    
    /**
     * Constructor con mensaje de error y causa subyacente.
     * 
     * @param message Mensaje descriptivo del error
     * @param cause Excepción que causó este error
     */
    public InvalidSignatureException(String message, Throwable cause) {
        super(message, cause, DEFAULT_STATUS, ERROR_CODE);
    }
    
    /**
     * Constructor completo con todos los parámetros.
     * 
     * @param message Mensaje descriptivo del error
     * @param cause Excepción que causó este error
     * @param additionalInfo Información adicional sobre el error
     */
    public InvalidSignatureException(String message, Throwable cause, String additionalInfo) {
        super(message, cause, DEFAULT_STATUS, ERROR_CODE, additionalInfo);
    }
    
    /**
     * Constructor específico para validación de firma de webhook.
     * 
     * @param webhookName Nombre del webhook afectado
     * @param receivedSignature Firma recibida (puede ser null)
     */
    public InvalidSignatureException(String webhookName, String receivedSignature) {
        super(
            "Firma HMAC inválida para el webhook",
            DEFAULT_STATUS,
            ERROR_CODE,
            String.format("Webhook: %s", webhookName)
        );
    }
    
    /**
     * Genera una instancia para cabecera de firma ausente.
     * 
     * @param webhookName Nombre del webhook afectado
     * @return Instancia de InvalidSignatureException
     */
    public static InvalidSignatureException missingSignatureHeader(String webhookName) {
        return new InvalidSignatureException(
            "Cabecera de firma ausente",
            String.format("Webhook: %s, Header: X-Webhook-Signature", webhookName)
        );
    }
    
    /**
     * Genera una instancia para formato de firma inválido.
     * 
     * @param webhookName Nombre del webhook afectado
     * @param receivedSignature Firma recibida en formato incorrecto
     * @return Instancia de InvalidSignatureException
     */
    public static InvalidSignatureException invalidSignatureFormat(String webhookName, String receivedSignature) {
        return new InvalidSignatureException(
            "Formato de firma inválido",
            String.format("Webhook: %s, Expected format: 'sha256=HEX_STRING'", webhookName)
        );
    }
    
    /**
     * Genera una instancia para firma que no coincide.
     * 
     * @param webhookName Nombre del webhook afectado
     * @param expectedSignature Firma esperada
     * @param receivedSignature Firma recibida
     * @return Instancia de InvalidSignatureException
     */
    public static InvalidSignatureException signatureMismatch(
            String webhookName, String expectedSignature, String receivedSignature) {
        
        // Ofuscar parcialmente las firmas para no exponer información sensible
        String obfuscatedExpected = obfuscateSignature(expectedSignature);
        String obfuscatedReceived = obfuscateSignature(receivedSignature);
        
        return new InvalidSignatureException(
            "La firma recibida no coincide con la esperada",
            String.format("Webhook: %s", webhookName)
        );
    }
    
    /**
     * Genera una instancia para secreto de webhook no encontrado.
     * 
     * @param webhookName Nombre del webhook afectado
     * @return Instancia de InvalidSignatureException
     */
    public static InvalidSignatureException secretNotFound(String webhookName) {
        return new InvalidSignatureException(
            "Secreto no encontrado para el webhook",
            String.format("Webhook: %s", webhookName)
        );
    }
    
    /**
     * Genera una instancia para error en cálculo de firma.
     * 
     * @param webhookName Nombre del webhook afectado
     * @param cause Excepción que causó el error
     * @return Instancia de InvalidSignatureException
     */
    public static InvalidSignatureException signatureCalculationError(String webhookName, Throwable cause) {
        return new InvalidSignatureException(
            "Error al calcular la firma HMAC",
            cause,
            String.format("Webhook: %s", webhookName)
        );
    }
    
    /**
     * Ofusca una firma para no exponer información sensible en logs o mensajes de error.
     * Por ejemplo, convierte "sha256=a1b2c3d4e5f6..." en "sha256=a1b2...f6" 
     */
    private static String obfuscateSignature(String signature) {
        if (signature == null || signature.length() < 10) {
            return signature;
        }
        
        if (signature.contains("=")) {
            // Formato con prefijo (sha256=abc123)
            String[] parts = signature.split("=", 2);
            String prefix = parts[0] + "=";
            String hash = parts[1];
            
            if (hash.length() <= 8) {
                return signature; // Demasiado corto para ofuscar
            }
            
            return prefix + hash.substring(0, 4) + "..." + hash.substring(hash.length() - 2);
        } else {
            // Formato sin prefijo (sólo hash)
            if (signature.length() <= 8) {
                return signature; // Demasiado corto para ofuscar
            }
            
            return signature.substring(0, 4) + "..." + signature.substring(signature.length() - 2);
        }
    }
}