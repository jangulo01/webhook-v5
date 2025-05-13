package com.yourcompany.webhookservice.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Utility class for validation operations used throughout the webhook service.
 * Provides methods to validate various types of input data and formats.
 */
public class ValidationUtils {

    private static final Logger logger = LoggerFactory.getLogger(ValidationUtils.class);

    private static final ObjectMapper objectMapper = JsonUtils.getObjectMapper();
    
    // Common validation patterns
    private static final Pattern EMAIL_PATTERN = 
            Pattern.compile("^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$");
    
    private static final Pattern WEBHOOK_NAME_PATTERN = 
            Pattern.compile("^[a-zA-Z0-9_\\-\\.]{1,64}$");
    
    private static final Pattern HMAC_HEX_PATTERN = 
            Pattern.compile("^[0-9a-fA-F]{32,128}$");

    private ValidationUtils() {
        // Utility class should not be instantiated
        throw new IllegalStateException("Utility class");
    }
    
    /**
     * Validate if a string is not null or empty.
     * 
     * @param str The string to validate
     * @param fieldName Name of the field (for error messages)
     * @return The validated string
     * @throws IllegalArgumentException if validation fails
     */
    public static String validateRequired(String str, String fieldName) {
        if (!StringUtils.hasText(str)) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return str;
    }
    
    /**
     * Validate if an object is not null.
     * 
     * @param <T> The type of object
     * @param obj The object to validate
     * @param fieldName Name of the field (for error messages)
     * @return The validated object
     * @throws IllegalArgumentException if validation fails
     */
    public static <T> T validateNotNull(T obj, String fieldName) {
        if (obj == null) {
            throw new IllegalArgumentException(fieldName + " cannot be null");
        }
        return obj;
    }
    
    /**
     * Validate if a collection is not null or empty.
     * 
     * @param <T> The type of collection
     * @param collection The collection to validate
     * @param fieldName Name of the field (for error messages)
     * @return The validated collection
     * @throws IllegalArgumentException if validation fails
     */
    public static <T extends Collection<?>> T validateNotEmpty(T collection, String fieldName) {
        if (collection == null || collection.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be null or empty");
        }
        return collection;
    }
    
    /**
     * Validate if a map is not null or empty.
     * 
     * @param <T> The type of map
     * @param map The map to validate
     * @param fieldName Name of the field (for error messages)
     * @return The validated map
     * @throws IllegalArgumentException if validation fails
     */
    public static <T extends Map<?, ?>> T validateNotEmpty(T map, String fieldName) {
        if (map == null || map.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be null or empty");
        }
        return map;
    }
    
    /**
     * Validate if a string is a valid URL.
     * 
     * @param urlString The URL string to validate
     * @param fieldName Name of the field (for error messages)
     * @return The validated URL string
     * @throws IllegalArgumentException if validation fails
     */
    public static String validateUrl(String urlString, String fieldName) {
        if (!StringUtils.hasText(urlString)) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        
        try {
            new URL(urlString);
            return urlString;
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(fieldName + " is not a valid URL: " + urlString);
        }
    }
    
    /**
     * Validate if a string is a valid HTTP/HTTPS URL.
     * 
     * @param urlString The URL string to validate
     * @param fieldName Name of the field (for error messages)
     * @return The validated URL string
     * @throws IllegalArgumentException if validation fails
     */
    public static String validateHttpUrl(String urlString, String fieldName) {
        String url = validateUrl(urlString, fieldName);
        
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new IllegalArgumentException(fieldName + " must be an HTTP or HTTPS URL: " + url);
        }
        
        return url;
    }
    
    /**
     * Validate if a string is a valid email address.
     * 
     * @param email The email address to validate
     * @param fieldName Name of the field (for error messages)
     * @return The validated email address
     * @throws IllegalArgumentException if validation fails
     */
    public static String validateEmail(String email, String fieldName) {
        if (!StringUtils.hasText(email)) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException(fieldName + " is not a valid email address: " + email);
        }
        
        return email;
    }
    
    /**
     * Validate if a string is valid JSON.
     * 
     * @param json The JSON string to validate
     * @param fieldName Name of the field (for error messages)
     * @return The validated JSON string
     * @throws IllegalArgumentException if validation fails
     */
    public static String validateJson(String json, String fieldName) {
        if (!StringUtils.hasText(json)) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        
        try {
            objectMapper.readTree(json);
            return json;
        } catch (Exception e) {
            throw new IllegalArgumentException(fieldName + " is not valid JSON: " + e.getMessage());
        }
    }
    
    /**
     * Validate if a string is a valid UUID.
     * 
     * @param uuidString The UUID string to validate
     * @param fieldName Name of the field (for error messages)
     * @return The validated UUID string
     * @throws IllegalArgumentException if validation fails
     */
    public static String validateUuid(String uuidString, String fieldName) {
        if (!StringUtils.hasText(uuidString)) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        
        try {
            UUID.fromString(uuidString);
            return uuidString;
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(fieldName + " is not a valid UUID: " + uuidString);
        }
    }
    
    /**
     * Validate if a string is a valid webhook name.
     * 
     * @param name The webhook name to validate
     * @param fieldName Name of the field (for error messages)
     * @return The validated webhook name
     * @throws IllegalArgumentException if validation fails
     */
    public static String validateWebhookName(String name, String fieldName) {
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        
        if (!WEBHOOK_NAME_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException(fieldName + 
                    " must contain only letters, numbers, underscores, hyphens, and periods, " +
                    "and be between 1 and 64 characters long");
        }
        
        return name;
    }
    
    /**
     * Validate if a string is a valid HMAC hex signature.
     * 
     * @param signature The signature to validate
     * @param fieldName Name of the field (for error messages)
     * @return The validated signature
     * @throws IllegalArgumentException if validation fails
     */
    public static String validateHmacSignature(String signature, String fieldName) {
        if (!StringUtils.hasText(signature)) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        
        if (!HMAC_HEX_PATTERN.matcher(signature).matches()) {
            throw new IllegalArgumentException(fieldName + " is not a valid HMAC signature format");
        }
        
        return signature;
    }
    
    /**
     * Validate if a number is within a specified range.
     * 
     * @param value The value to validate
     * @param min Minimum allowed value (inclusive)
     * @param max Maximum allowed value (inclusive)
     * @param fieldName Name of the field (for error messages)
     * @return The validated value
     * @throws IllegalArgumentException if validation fails
     */
    public static int validateRange(int value, int min, int max, String fieldName) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(fieldName + " must be between " + min + " and " + max);
        }
        
        return value;
    }
    
    /**
     * Validate if a string is one of the specified allowed values.
     * 
     * @param value The value to validate
     * @param allowedValues Array of allowed values
     * @param fieldName Name of the field (for error messages)
     * @return The validated value
     * @throws IllegalArgumentException if validation fails
     */
    public static String validateAllowedValues(String value, String[] allowedValues, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        
        for (String allowedValue : allowedValues) {
            if (value.equals(allowedValue)) {
                return value;
            }
        }
        
        throw new IllegalArgumentException(fieldName + " must be one of: " + String.join(", ", allowedValues));
    }
    
    /**
     * Validate if a string meets the length requirements.
     * 
     * @param str The string to validate
     * @param minLength Minimum allowed length
     * @param maxLength Maximum allowed length
     * @param fieldName Name of the field (for error messages)
     * @return The validated string
     * @throws IllegalArgumentException if validation fails
     */
    public static String validateLength(String str, int minLength, int maxLength, String fieldName) {
        if (str == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        
        int length = str.length();
        
        if (length < minLength || length > maxLength) {
            throw new IllegalArgumentException(fieldName + 
                    " length must be between " + minLength + " and " + maxLength + " characters");
        }
        
        return str;
    }
    
    /**
     * Validate if a backoff strategy is valid.
     * 
     * @param strategy The backoff strategy to validate
     * @param fieldName Name of the field (for error messages)
     * @return The validated strategy
     * @throws IllegalArgumentException if validation fails
     */
    public static String validateBackoffStrategy(String strategy, String fieldName) {
        return validateAllowedValues(strategy, new String[]{"linear", "exponential"}, fieldName);
    }
    
    /**
     * Validate if a payload is valid for webhook processing.
     * 
     * @param payload The payload to validate
     * @param fieldName Name of the field (for error messages)
     * @return The validated payload as a JsonNode
     * @throws IllegalArgumentException if validation fails
     */
    public static JsonNode validateWebhookPayload(String payload, String fieldName) {
        if (!StringUtils.hasText(payload)) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        
        try {
            return objectMapper.readTree(payload);
        } catch (Exception e) {
            throw new IllegalArgumentException(fieldName + " is not valid JSON: " + e.getMessage());
        }
    }
    
    /**
     * Validate webhook configuration parameters.
     * 
     * @param name The webhook name
     * @param targetUrl The target URL
     * @param secret The secret key
     * @throws IllegalArgumentException if validation fails
     */
    public static void validateWebhookConfig(String name, String targetUrl, String secret) {
        validateWebhookName(name, "Webhook name");
        validateHttpUrl(targetUrl, "Target URL");
        validateRequired(secret, "Secret");
    }
    
    /**
     * Validate if headers are valid JSON or properly formatted.
     * 
     * @param headers The headers to validate (JSON string or null)
     * @param fieldName Name of the field (for error messages)
     * @return The validated headers
     * @throws IllegalArgumentException if validation fails
     */
    public static String validateHeaders(String headers, String fieldName) {
        if (headers == null || headers.isEmpty()) {
            return headers;
        }
        
        try {
            objectMapper.readTree(headers);
            return headers;
        } catch (Exception e) {
            throw new IllegalArgumentException(fieldName + " must be valid JSON: " + e.getMessage());
        }
    }
    
    /**
     * Validate retry parameters.
     * 
     * @param maxRetries Maximum number of retries
     * @param initialInterval Initial interval in seconds
     * @param backoffFactor Backoff factor
     * @param maxInterval Maximum interval in seconds
     * @throws IllegalArgumentException if validation fails
     */
    public static void validateRetryParams(
            int maxRetries,
            int initialInterval,
            double backoffFactor,
            int maxInterval) {
        
        if (maxRetries < 0) {
            throw new IllegalArgumentException("Max retries cannot be negative");
        }
        
        if (initialInterval <= 0) {
            throw new IllegalArgumentException("Initial interval must be greater than zero");
        }
        
        if (backoffFactor <= 0) {
            throw new IllegalArgumentException("Backoff factor must be greater than zero");
        }
        
        if (maxInterval <= 0) {
            throw new IllegalArgumentException("Max interval must be greater than zero");
        }
        
        if (maxInterval < initialInterval) {
            throw new IllegalArgumentException("Max interval must be greater than or equal to initial interval");
        }
    }
    
    /**
     * Check if a URL is valid without throwing an exception.
     * 
     * @param urlString The URL string to check
     * @return true if valid, false otherwise
     */
    public static boolean isValidUrl(String urlString) {
        if (!StringUtils.hasText(urlString)) {
            return false;
        }
        
        try {
            new URL(urlString);
            return true;
        } catch (MalformedURLException e) {
            return false;
        }
    }
    
    /**
     * Check if a string is a valid UUID without throwing an exception.
     * 
     * @param str The string to check
     * @return true if valid UUID, false otherwise
     */
    public static boolean isUuid(String str) {
        if (!StringUtils.hasText(str)) {
            return false;
        }
        
        try {
            UUID.fromString(str);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
    
    /**
     * Check if a string is valid JSON without throwing an exception.
     * 
     * @param str The string to check
     * @return true if valid JSON, false otherwise
     */
    public static boolean isJson(String str) {
        return JsonUtils.isValidJson(str);
    }
    
    /**
     * Check if a webhook name is valid without throwing an exception.
     * 
     * @param name The name to check
     * @return true if valid webhook name, false otherwise
     */
    public static boolean isValidWebhookName(String name) {
        if (!StringUtils.hasText(name)) {
            return false;
        }
        
        return WEBHOOK_NAME_PATTERN.matcher(name).matches();
    }
}