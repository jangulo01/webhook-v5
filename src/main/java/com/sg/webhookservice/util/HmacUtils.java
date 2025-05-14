package com.sg.webhookservice.util;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Utility class for generating and verifying HMAC signatures for webhook payloads.
 * Provides compatibility with the generador_hmac.py module from the Python version.
 */
public class HmacUtils {

    private static final Logger logger = LoggerFactory.getLogger(HmacUtils.class);
    
    // Default algorithm for HMAC
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    
    // For JSON normalization
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    private HmacUtils() {
        // Utility class should not be instantiated
        throw new IllegalStateException("Utility class");
    }
    
    /**
     * Generate HMAC signature for a payload with detailed logging.
     * 
     * @param payload The payload to sign (can be String, byte[], or Object)
     * @param secret The secret key for signing
     * @param logDetails Whether to log detailed information about the signing process
     * @return HMAC signature as hexadecimal string
     * @throws IllegalArgumentException if the payload or secret is invalid
     */
    public static String generateSignature(Object payload, String secret, boolean logDetails) {
        if (payload == null) {
            throw new IllegalArgumentException("Payload cannot be null");
        }
        
        if (secret == null || secret.isEmpty()) {
            throw new IllegalArgumentException("Secret cannot be null or empty");
        }
        
        String normalizedPayload = normalizePayload(payload);
        
        if (logDetails) {
            logger.info("Generating signature for normalized payload: {}...", 
                    normalizedPayload.length() > 100 ? 
                            normalizedPayload.substring(0, 100) + "..." : 
                            normalizedPayload);
        }
        
        try {
            byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
            byte[] payloadBytes = normalizedPayload.getBytes(StandardCharsets.UTF_8);
            
            SecretKeySpec secretKeySpec = new SecretKeySpec(secretBytes, HMAC_ALGORITHM);
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(secretKeySpec);
            
            byte[] signatureBytes = mac.doFinal(payloadBytes);
            
            // Convert to hexadecimal string
            String signature = HexFormat.of().formatHex(signatureBytes);
            
            if (logDetails) {
                logger.info("Generated signature: {}", signature);
            }
            
            return signature;
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            logger.error("Error generating HMAC signature", e);
            throw new IllegalStateException("Error generating signature: " + e.getMessage(), e);
        }
    }
    
    /**
     * Generate HMAC signature for a payload (simplified method).
     * 
     * @param payload The payload to sign
     * @param secret The secret key for signing
     * @return HMAC signature as hexadecimal string
     */
    public static String generateSignature(Object payload, String secret) {
        return generateSignature(payload, secret, false);
    }
    
    /**
     * Generate HMAC signature in Base64 format.
     * 
     * @param payload The payload to sign
     * @param secret The secret key for signing
     * @return HMAC signature as Base64 string
     */
    public static String generateSignatureBase64(Object payload, String secret) {
        if (payload == null) {
            throw new IllegalArgumentException("Payload cannot be null");
        }
        
        if (secret == null || secret.isEmpty()) {
            throw new IllegalArgumentException("Secret cannot be null or empty");
        }
        
        String normalizedPayload = normalizePayload(payload);
        
        try {
            byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
            byte[] payloadBytes = normalizedPayload.getBytes(StandardCharsets.UTF_8);
            
            SecretKeySpec secretKeySpec = new SecretKeySpec(secretBytes, HMAC_ALGORITHM);
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(secretKeySpec);
            
            byte[] signatureBytes = mac.doFinal(payloadBytes);
            
            // Convert to Base64 string
            return Base64.getEncoder().encodeToString(signatureBytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            logger.error("Error generating Base64 HMAC signature", e);
            throw new IllegalStateException("Error generating signature: " + e.getMessage(), e);
        }
    }
    
    /**
     * Verify HMAC signature for a payload.
     * 
     * @param payload The payload to verify
     * @param providedSignature The signature to verify
     * @param secret The secret key for verification
     * @return true if signature is valid, false otherwise
     */
    public static boolean verifySignature(Object payload, String providedSignature, String secret) {
        if (payload == null || providedSignature == null || secret == null) {
            return false;
        }
        
        try {
            String expectedSignature = generateSignature(payload, secret);
            
            // Constant-time comparison to prevent timing attacks
            return constantTimeEquals(expectedSignature, providedSignature);
        } catch (Exception e) {
            logger.error("Error verifying signature", e);
            return false;
        }
    }
    
    /**
     * Verify HMAC signature in Base64 format.
     * 
     * @param payload The payload to verify
     * @param providedSignature The Base64 signature to verify
     * @param secret The secret key for verification
     * @return true if signature is valid, false otherwise
     */
    public static boolean verifySignatureBase64(Object payload, String providedSignature, String secret) {
        if (payload == null || providedSignature == null || secret == null) {
            return false;
        }
        
        try {
            String expectedSignature = generateSignatureBase64(payload, secret);
            
            // Constant-time comparison to prevent timing attacks
            return constantTimeEquals(expectedSignature, providedSignature);
        } catch (Exception e) {
            logger.error("Error verifying Base64 signature", e);
            return false;
        }
    }
    
    /**
     * Constant-time string comparison to prevent timing attacks.
     * 
     * @param a First string
     * @param b Second string
     * @return true if strings are equal, false otherwise
     */
    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        
        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);
        
        if (aBytes.length != bBytes.length) {
            return false;
        }
        
        int result = 0;
        for (int i = 0; i < aBytes.length; i++) {
            result |= aBytes[i] ^ bBytes[i];
        }
        
        return result == 0;
    }
    
    /**
     * Normalize payload to ensure consistent signatures across different platforms.
     * Handles JSON objects, strings, and other types.
     * 
     * @param payload The payload to normalize
     * @return Normalized payload as string
     */
    private static String normalizePayload(Object payload) {
        if (payload == null) {
            return "";
        }
        
        // If payload is already a string
        if (payload instanceof String) {
            String stringPayload = (String) payload;
            
            // Check if it's a JSON string and normalize it
            try {
                JsonNode jsonNode = objectMapper.readTree(stringPayload);
                return objectMapper.writeValueAsString(jsonNode);
            } catch (JsonParseException e) {
                // Not valid JSON, return as is
                return stringPayload;
            } catch (JsonProcessingException e) {
                logger.warn("Error processing JSON string", e);
                return stringPayload;
            }
        }
        
        // If payload is a byte array
        if (payload instanceof byte[]) {
            try {
                // Try to parse as JSON first
                JsonNode jsonNode = objectMapper.readTree((byte[]) payload);
                return objectMapper.writeValueAsString(jsonNode);
            } catch (Exception e) {
                // Not JSON, convert to string
                return new String((byte[]) payload, StandardCharsets.UTF_8);
            }
        }
        
        // For other objects, try to convert to JSON
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            logger.warn("Error serializing object to JSON", e);
            return payload.toString();
        }
    }
    
    /**
     * Extract HMAC signature from HTTP headers.
     * 
     * @param signatureHeader The signature header value
     * @return The extracted signature or null if not found
     */
    public static String extractSignatureFromHeader(String signatureHeader) {
        if (!StringUtils.hasText(signatureHeader)) {
            return null;
        }
        
        // Handle various signature header formats
        // Format 1: Just the signature
        if (!signatureHeader.contains("=") && !signatureHeader.contains(" ")) {
            return signatureHeader.trim();
        }
        
        // Format 2: sha256=signature
        if (signatureHeader.contains("=")) {
            String[] parts = signatureHeader.split("=", 2);
            if (parts.length == 2) {
                return parts[1].trim();
            }
        }
        
        // Format 3: Signature signature_value
        if (signatureHeader.toLowerCase().startsWith("signature ")) {
            return signatureHeader.substring(10).trim();
        }
        
        logger.warn("Unrecognized signature header format: {}", signatureHeader);
        return signatureHeader.trim();
    }
    
    /**
     * Generate signature for a request body and compare with the provided signature.
     * 
     * @param requestBody The request body
     * @param signatureHeader The signature header
     * @param secret The secret key
     * @return true if signature is valid, false otherwise
     */
    public static boolean validateWebhookSignature(String requestBody, String signatureHeader, String secret) {
        if (!StringUtils.hasText(requestBody) || !StringUtils.hasText(signatureHeader) || !StringUtils.hasText(secret)) {
            return false;
        }
        
        String extractedSignature = extractSignatureFromHeader(signatureHeader);
        if (extractedSignature == null) {
            return false;
        }
        
        // Determine if it's a hex or base64 signature
        if (isHexSignature(extractedSignature)) {
            return verifySignature(requestBody, extractedSignature, secret);
        } else {
            return verifySignatureBase64(requestBody, extractedSignature, secret);
        }
    }
    
    /**
     * Check if a signature is in hexadecimal format.
     * 
     * @param signature The signature to check
     * @return true if it's a hex signature, false otherwise
     */
    private static boolean isHexSignature(String signature) {
        // A hex signature should only contain hex characters
        return signature.matches("^[0-9a-fA-F]+$");
    }
    
    /**
     * Generate HMAC signature with SHA-1 algorithm (for compatibility with some services).
     * 
     * @param payload The payload to sign
     * @param secret The secret key
     * @return HMAC-SHA1 signature as hexadecimal string
     */
    public static String generateSha1Signature(Object payload, String secret) {
        if (payload == null) {
            throw new IllegalArgumentException("Payload cannot be null");
        }
        
        if (secret == null || secret.isEmpty()) {
            throw new IllegalArgumentException("Secret cannot be null or empty");
        }
        
        String normalizedPayload = normalizePayload(payload);
        
        try {
            byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
            byte[] payloadBytes = normalizedPayload.getBytes(StandardCharsets.UTF_8);
            
            SecretKeySpec secretKeySpec = new SecretKeySpec(secretBytes, "HmacSHA1");
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(secretKeySpec);
            
            byte[] signatureBytes = mac.doFinal(payloadBytes);
            
            // Convert to hexadecimal string
            return HexFormat.of().formatHex(signatureBytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            logger.error("Error generating SHA-1 HMAC signature", e);
            throw new IllegalStateException("Error generating signature: " + e.getMessage(), e);
        }
    }
}