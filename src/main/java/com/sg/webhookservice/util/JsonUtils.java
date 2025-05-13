package com.yourcompany.webhookservice.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Utility class for JSON operations used throughout the webhook service.
 * Provides methods for parsing, serializing, validating, and manipulating JSON data.
 */
public class JsonUtils {

    private static final Logger logger = LoggerFactory.getLogger(JsonUtils.class);
    
    // Singleton ObjectMapper instance configured for application needs
    private static final ObjectMapper objectMapper = createObjectMapper();
    
    private JsonUtils() {
        // Utility class should not be instantiated
        throw new IllegalStateException("Utility class");
    }
    
    /**
     * Creates and configures an ObjectMapper with application-specific settings.
     * 
     * @return Configured ObjectMapper instance
     */
    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        
        // Register JavaTimeModule to handle Java 8 date/time types
        mapper.registerModule(new JavaTimeModule());
        
        // Configure serialization features
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        
        return mapper;
    }
    
    /**
     * Get access to the configured ObjectMapper instance.
     * 
     * @return The ObjectMapper instance
     */
    public static ObjectMapper getObjectMapper() {
        return objectMapper;
    }
    
    /**
     * Convert an object to its JSON string representation.
     * 
     * @param object The object to serialize
     * @return JSON string representation
     * @throws IllegalArgumentException if serialization fails
     */
    public static String toJson(Object object) {
        if (object == null) {
            return null;
        }
        
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            logger.error("Error serializing object to JSON", e);
            throw new IllegalArgumentException("Failed to serialize object to JSON: " + e.getMessage(), e);
        }
    }
    
    /**
     * Convert an object to a pretty-printed JSON string.
     * 
     * @param object The object to serialize
     * @return Pretty-printed JSON string
     * @throws IllegalArgumentException if serialization fails
     */
    public static String toPrettyJson(Object object) {
        if (object == null) {
            return null;
        }
        
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(object);
        } catch (JsonProcessingException e) {
            logger.error("Error serializing object to pretty JSON", e);
            throw new IllegalArgumentException("Failed to serialize object to pretty JSON: " + e.getMessage(), e);
        }
    }
    
    /**
     * Parse a JSON string into a JsonNode.
     * 
     * @param json The JSON string to parse
     * @return JsonNode representation
     * @throws IllegalArgumentException if parsing fails
     */
    public static JsonNode parseJson(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            logger.error("Error parsing JSON string", e);
            throw new IllegalArgumentException("Invalid JSON: " + e.getMessage(), e);
        }
    }
    
    /**
     * Convert a JSON string to a specific Java object.
     * 
     * @param <T> The target object type
     * @param json The JSON string to parse
     * @param clazz The class of the target object
     * @return An instance of the target class
     * @throws IllegalArgumentException if parsing fails
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        
        try {
            return objectMapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            logger.error("Error deserializing JSON to {}", clazz.getName(), e);
            throw new IllegalArgumentException("Failed to deserialize JSON: " + e.getMessage(), e);
        }
    }
    
    /**
     * Convert a JSON string to a generic Java type using TypeReference.
     * Useful for collections and other generic types.
     * 
     * @param <T> The target type
     * @param json The JSON string to parse
     * @param typeReference The TypeReference describing the target type
     * @return An instance of the target type
     * @throws IllegalArgumentException if parsing fails
     */
    public static <T> T fromJson(String json, TypeReference<T> typeReference) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        
        try {
            return objectMapper.readValue(json, typeReference);
        } catch (JsonProcessingException e) {
            logger.error("Error deserializing JSON to complex type", e);
            throw new IllegalArgumentException("Failed to deserialize JSON: " + e.getMessage(), e);
        }
    }
    
    /**
     * Convert a JsonNode to a specific Java object.
     * 
     * @param <T> The target object type
     * @param node The JsonNode to convert
     * @param clazz The class of the target object
     * @return An instance of the target class
     * @throws IllegalArgumentException if conversion fails
     */
    public static <T> T fromJsonNode(JsonNode node, Class<T> clazz) {
        if (node == null) {
            return null;
        }
        
        try {
            return objectMapper.treeToValue(node, clazz);
        } catch (JsonProcessingException e) {
            logger.error("Error converting JsonNode to {}", clazz.getName(), e);
            throw new IllegalArgumentException("Failed to convert JsonNode: " + e.getMessage(), e);
        }
    }
    
    /**
     * Convert a JSON string to a Map.
     * 
     * @param json The JSON string to convert
     * @return Map representation of the JSON
     * @throws IllegalArgumentException if parsing fails
     */
    public static Map<String, Object> jsonToMap(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyMap();
        }
        
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            logger.error("Error converting JSON to Map", e);
            throw new IllegalArgumentException("Failed to convert JSON to Map: " + e.getMessage(), e);
        }
    }
    
    /**
     * Convert a Map to a JSON string.
     * 
     * @param map The Map to convert
     * @return JSON string representation
     * @throws IllegalArgumentException if serialization fails
     */
    public static String mapToJson(Map<String, ?> map) {
        if (map == null) {
            return null;
        }
        
        return toJson(map);
    }
    
    /**
     * Validate if a string is valid JSON.
     * 
     * @param json The string to validate
     * @return true if valid JSON, false otherwise
     */
    public static boolean isValidJson(String json) {
        if (!StringUtils.hasText(json)) {
            return false;
        }
        
        try {
            objectMapper.readTree(json);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
    
    /**
     * Create a new, empty ObjectNode.
     * 
     * @return A new ObjectNode
     */
    public static ObjectNode createObjectNode() {
        return objectMapper.createObjectNode();
    }
    
    /**
     * Create a new, empty ArrayNode.
     * 
     * @return A new ArrayNode
     */
    public static ArrayNode createArrayNode() {
        return objectMapper.createArrayNode();
    }
    
    /**
     * Merge two JSON objects (represented as strings) into one.
     * Properties in the second object override those in the first if there are conflicts.
     * 
     * @param json1 The first JSON string
     * @param json2 The second JSON string (with higher precedence)
     * @return Merged JSON string
     * @throws IllegalArgumentException if parsing fails
     */
    @SuppressWarnings("unchecked")
    public static String mergeJson(String json1, String json2) {
        if (!StringUtils.hasText(json1)) {
            return json2;
        }
        
        if (!StringUtils.hasText(json2)) {
            return json1;
        }
        
        try {
            Map<String, Object> map1 = objectMapper.readValue(json1, Map.class);
            Map<String, Object> map2 = objectMapper.readValue(json2, Map.class);
            
            // Merge map2 into map1
            map2.forEach((key, value) -> map1.put(key, value));
            
            return objectMapper.writeValueAsString(map1);
        } catch (IOException e) {
            logger.error("Error merging JSON strings", e);
            throw new IllegalArgumentException("Failed to merge JSON: " + e.getMessage(), e);
        }
    }
    
    /**
     * Normalize a JSON string by parsing and re-serializing it.
     * This removes formatting variations and provides a consistent string representation.
     * 
     * @param json The JSON string to normalize
     * @return Normalized JSON string
     * @throws IllegalArgumentException if parsing fails
     */
    public static String normalizeJson(String json) {
        if (!StringUtils.hasText(json)) {
            return json;
        }
        
        try {
            JsonNode jsonNode = objectMapper.readTree(json);
            return objectMapper.writeValueAsString(jsonNode);
        } catch (IOException e) {
            logger.error("Error normalizing JSON string", e);
            throw new IllegalArgumentException("Invalid JSON: " + e.getMessage(), e);
        }
    }
    
    /**
     * Extract a specific field from a JSON string.
     * 
     * @param json The JSON string
     * @param fieldPath Path to the field using dot notation (e.g., "user.address.city")
     * @return String value of the field or null if not found
     * @throws IllegalArgumentException if parsing fails
     */
    public static String extractField(String json, String fieldPath) {
        if (!StringUtils.hasText(json) || !StringUtils.hasText(fieldPath)) {
            return null;
        }
        
        try {
            JsonNode root = objectMapper.readTree(json);
            String[] pathParts = fieldPath.split("\\.");
            
            JsonNode current = root;
            for (String part : pathParts) {
                current = current.path(part);
                if (current.isMissingNode()) {
                    return null;
                }
            }
            
            return current.isValueNode() ? current.asText() : current.toString();
        } catch (IOException e) {
            logger.error("Error extracting field from JSON", e);
            throw new IllegalArgumentException("Failed to extract field: " + e.getMessage(), e);
        }
    }
    
    /**
     * Convert a JsonNode array to a List of strings.
     * 
     * @param arrayNode The JsonNode array
     * @return List of strings or empty list if not an array
     */
    public static List<String> arrayNodeToStringList(JsonNode arrayNode) {
        if (arrayNode == null || !arrayNode.isArray()) {
            return Collections.emptyList();
        }
        
        return StreamSupport.stream(arrayNode.spliterator(), false)
                .map(JsonNode::asText)
                .collect(Collectors.toList());
    }
    
    /**
     * Safely get a string value from a JsonNode, with fallback.
     * 
     * @param node The JsonNode
     * @param fieldName The field name to get
     * @param defaultValue Default value if field is missing or not a string
     * @return Field value or default value
     */
    public static String getStringFieldSafe(JsonNode node, String fieldName, String defaultValue) {
        if (node == null || !node.has(fieldName)) {
            return defaultValue;
        }
        
        JsonNode fieldNode = node.get(fieldName);
        if (fieldNode.isTextual()) {
            return fieldNode.asText();
        }
        
        return defaultValue;
    }
    
    /**
     * Safely get an integer value from a JsonNode, with fallback.
     * 
     * @param node The JsonNode
     * @param fieldName The field name to get
     * @param defaultValue Default value if field is missing or not an integer
     * @return Field value or default value
     */
    public static int getIntFieldSafe(JsonNode node, String fieldName, int defaultValue) {
        if (node == null || !node.has(fieldName)) {
            return defaultValue;
        }
        
        JsonNode fieldNode = node.get(fieldName);
        if (fieldNode.isInt()) {
            return fieldNode.asInt();
        }
        
        return defaultValue;
    }
    
    /**
     * Safely get a double value from a JsonNode, with fallback.
     * 
     * @param node The JsonNode
     * @param fieldName The field name to get
     * @param defaultValue Default value if field is missing or not a number
     * @return Field value or default value
     */
    public static double getDoubleFieldSafe(JsonNode node, String fieldName, double defaultValue) {
        if (node == null || !node.has(fieldName)) {
            return defaultValue;
        }
        
        JsonNode fieldNode = node.get(fieldName);
        if (fieldNode.isNumber()) {
            return fieldNode.asDouble();
        }
        
        return defaultValue;
    }
    
    /**
     * Safely get a boolean value from a JsonNode, with fallback.
     * 
     * @param node The JsonNode
     * @param fieldName The field name to get
     * @param defaultValue Default value if field is missing or not a boolean
     * @return Field value or default value
     */
    public static boolean getBooleanFieldSafe(JsonNode node, String fieldName, boolean defaultValue) {
        if (node == null || !node.has(fieldName)) {
            return defaultValue;
        }
        
        JsonNode fieldNode = node.get(fieldName);
        if (fieldNode.isBoolean()) {
            return fieldNode.asBoolean();
        }
        
        return defaultValue;
    }
    
    /**
     * Convert a JSON string to a pretty-printed version.
     * 
     * @param json The JSON string to format
     * @return Pretty-printed JSON string
     * @throws IllegalArgumentException if parsing fails
     */
    public static String prettyPrintJson(String json) {
        if (!StringUtils.hasText(json)) {
            return json;
        }
        
        try {
            JsonNode jsonNode = objectMapper.readTree(json);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode);
        } catch (IOException e) {
            logger.error("Error pretty-printing JSON", e);
            throw new IllegalArgumentException("Invalid JSON: " + e.getMessage(), e);
        }
    }
}