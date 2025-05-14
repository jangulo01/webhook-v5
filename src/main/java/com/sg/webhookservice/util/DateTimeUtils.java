package com.sg.webhookservice.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * Utility class for date and time operations used throughout the webhook service.
 * Provides methods for conversion, formatting, and calculation related to dates and times.
 */
public class DateTimeUtils {

    private static final Logger logger = LoggerFactory.getLogger(DateTimeUtils.class);
    
    // Common date time formats
    public static final String ISO_8601_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX";
    public static final String RFC_3339_FORMAT = "yyyy-MM-dd'T'HH:mm:ssXXX";
    public static final String SIMPLE_DATE_FORMAT = "yyyy-MM-dd";
    public static final String SIMPLE_TIME_FORMAT = "HH:mm:ss";
    
    // Default formatters
    public static final DateTimeFormatter ISO_8601_FORMATTER = DateTimeFormatter.ofPattern(ISO_8601_FORMAT);
    public static final DateTimeFormatter RFC_3339_FORMATTER = DateTimeFormatter.ofPattern(RFC_3339_FORMAT);
    public static final DateTimeFormatter SIMPLE_DATE_FORMATTER = DateTimeFormatter.ofPattern(SIMPLE_DATE_FORMAT);
    public static final DateTimeFormatter SIMPLE_TIME_FORMATTER = DateTimeFormatter.ofPattern(SIMPLE_TIME_FORMAT);
    
    private DateTimeUtils() {
        // Utility class should not be instantiated
        throw new IllegalStateException("Utility class");
    }
    
    /**
     * Convert LocalDateTime to ISO 8601 string
     * 
     * @param dateTime The LocalDateTime to convert
     * @return ISO 8601 formatted string
     */
    public static String toIso8601String(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.atZone(ZoneId.systemDefault()).format(ISO_8601_FORMATTER);
    }
    
    /**
     * Convert LocalDateTime to RFC 3339 string
     * 
     * @param dateTime The LocalDateTime to convert
     * @return RFC 3339 formatted string
     */
    public static String toRfc3339String(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.atZone(ZoneId.systemDefault()).format(RFC_3339_FORMATTER);
    }
    
    /**
     * Parse ISO 8601 string to LocalDateTime
     * 
     * @param isoDateTimeString ISO 8601 formatted date time string
     * @return LocalDateTime object
     * @throws DateTimeParseException if the string cannot be parsed
     */
    public static LocalDateTime fromIso8601String(String isoDateTimeString) {
        if (isoDateTimeString == null || isoDateTimeString.isEmpty()) {
            return null;
        }
        try {
            return ZonedDateTime.parse(isoDateTimeString, ISO_8601_FORMATTER)
                    .toLocalDateTime();
        } catch (DateTimeParseException e) {
            logger.warn("Failed to parse ISO 8601 date: {}", isoDateTimeString, e);
            throw e;
        }
    }
    
    /**
     * Parse RFC 3339 string to LocalDateTime
     * 
     * @param rfcDateTimeString RFC 3339 formatted date time string
     * @return LocalDateTime object
     * @throws DateTimeParseException if the string cannot be parsed
     */
    public static LocalDateTime fromRfc3339String(String rfcDateTimeString) {
        if (rfcDateTimeString == null || rfcDateTimeString.isEmpty()) {
            return null;
        }
        try {
            return ZonedDateTime.parse(rfcDateTimeString, RFC_3339_FORMATTER)
                    .toLocalDateTime();
        } catch (DateTimeParseException e) {
            logger.warn("Failed to parse RFC 3339 date: {}", rfcDateTimeString, e);
            throw e;
        }
    }
    
    /**
     * Convert java.util.Date to LocalDateTime
     * 
     * @param date Date to convert
     * @return LocalDateTime
     */
    public static LocalDateTime fromDate(Date date) {
        if (date == null) {
            return null;
        }
        return LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }
    
    /**
     * Convert LocalDateTime to java.util.Date
     * 
     * @param dateTime LocalDateTime to convert
     * @return java.util.Date
     */
    public static Date toDate(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant());
    }
    
    /**
     * Get current time in ISO 8601 format
     * 
     * @return Current time as ISO 8601 string
     */
    public static String getCurrentTimeIso8601() {
        return toIso8601String(LocalDateTime.now());
    }
    
    /**
     * Get current time in RFC 3339 format
     * 
     * @return Current time as RFC 3339 string
     */
    public static String getCurrentTimeRfc3339() {
        return toRfc3339String(LocalDateTime.now());
    }
    
    /**
     * Calculate time elapsed since given LocalDateTime
     * 
     * @param startTime The start time
     * @return Duration representing elapsed time
     */
    public static Duration getElapsedTime(LocalDateTime startTime) {
        if (startTime == null) {
            return Duration.ZERO;
        }
        return Duration.between(startTime, LocalDateTime.now());
    }
    
    /**
     * Format duration to human-readable string
     * 
     * @param duration The duration to format
     * @return Human-readable duration string (e.g., "2h 30m 45s")
     */
    public static String formatDuration(Duration duration) {
        if (duration == null) {
            return "0s";
        }
        
        long days = duration.toDays();
        long hours = duration.toHours() % 24;
        long minutes = duration.toMinutes() % 60;
        long seconds = duration.getSeconds() % 60;
        
        StringBuilder formattedDuration = new StringBuilder();
        
        if (days > 0) {
            formattedDuration.append(days).append("d ");
        }
        
        if (hours > 0 || days > 0) {
            formattedDuration.append(hours).append("h ");
        }
        
        if (minutes > 0 || hours > 0 || days > 0) {
            formattedDuration.append(minutes).append("m ");
        }
        
        formattedDuration.append(seconds).append("s");
        
        return formattedDuration.toString().trim();
    }
    
    /**
     * Calculate when a next retry should occur based on backoff strategy
     * 
     * @param retryCount Current retry count
     * @param initialInterval Initial interval in seconds
     * @param backoffFactor Multiplier for backoff
     * @param maxInterval Maximum interval in seconds
     * @param strategy Backoff strategy ("linear" or "exponential")
     * @return LocalDateTime when next retry should occur
     */
    public static LocalDateTime calculateNextRetry(
            int retryCount, 
            int initialInterval, 
            double backoffFactor, 
            int maxInterval, 
            String strategy) {
        
        long delayInSeconds;
        
        if ("linear".equalsIgnoreCase(strategy)) {
            delayInSeconds = Math.min(initialInterval * (1 + retryCount), maxInterval);
        } else if ("exponential".equalsIgnoreCase(strategy)) {
            delayInSeconds = (long) Math.min(initialInterval * Math.pow(backoffFactor, retryCount), maxInterval);
        } else {
            // Default to linear if strategy is unrecognized
            delayInSeconds = Math.min(initialInterval * (1 + retryCount), maxInterval);
        }
        
        return LocalDateTime.now().plusSeconds(delayInSeconds);
    }
    
    /**
     * Check if a LocalDateTime is expired based on a maximum age
     * 
     * @param dateTime The LocalDateTime to check
     * @param maxAgeSeconds Maximum age in seconds
     * @return true if expired, false otherwise
     */
    public static boolean isExpired(LocalDateTime dateTime, long maxAgeSeconds) {
        if (dateTime == null) {
            return false;
        }
        
        LocalDateTime expirationTime = dateTime.plusSeconds(maxAgeSeconds);
        return LocalDateTime.now().isAfter(expirationTime);
    }
    
    /**
     * Get LocalDateTime from epoch milliseconds
     * 
     * @param epochMillis Epoch time in milliseconds
     * @return LocalDateTime
     */
    public static LocalDateTime fromEpochMillis(long epochMillis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault());
    }
    
    /**
     * Convert LocalDateTime to epoch milliseconds
     * 
     * @param dateTime LocalDateTime to convert
     * @return Epoch time in milliseconds
     */
    public static long toEpochMillis(LocalDateTime dateTime) {
        if (dateTime == null) {
            return 0;
        }
        return dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
    
    /**
     * Parse date time string with flexible format detection
     * 
     * @param dateTimeString Date time string in various formats
     * @return LocalDateTime if parsing succeeds, null otherwise
     */
    public static LocalDateTime parseFlexible(String dateTimeString) {
        if (dateTimeString == null || dateTimeString.isEmpty()) {
            return null;
        }
        
        // Try different formats
        try {
            // Try ISO 8601
            return fromIso8601String(dateTimeString);
        } catch (DateTimeParseException e1) {
            try {
                // Try RFC 3339
                return fromRfc3339String(dateTimeString);
            } catch (DateTimeParseException e2) {
                try {
                    // Try ISO local date time
                    return LocalDateTime.parse(dateTimeString);
                } catch (DateTimeParseException e3) {
                    try {
                        // Try ISO instant
                        return LocalDateTime.ofInstant(
                                Instant.parse(dateTimeString), 
                                ZoneId.systemDefault());
                    } catch (DateTimeParseException e4) {
                        logger.warn("Failed to parse date time string: {}", dateTimeString);
                        return null;
                    }
                }
            }
        }
    }
    
    /**
     * Calculate time remaining until a future LocalDateTime
     * 
     * @param futureTime The future time
     * @return Duration representing time remaining, or Duration.ZERO if in the past
     */
    public static Duration getTimeRemaining(LocalDateTime futureTime) {
        if (futureTime == null || futureTime.isBefore(LocalDateTime.now())) {
            return Duration.ZERO;
        }
        
        return Duration.between(LocalDateTime.now(), futureTime);
    }
    
    /**
     * Format time remaining until a future LocalDateTime as human-readable string
     * 
     * @param futureTime The future time
     * @return Human-readable time remaining string
     */
    public static String getFormattedTimeRemaining(LocalDateTime futureTime) {
        return formatDuration(getTimeRemaining(futureTime));
    }
    
    /**
     * Truncate LocalDateTime to the given temporal unit
     * 
     * @param dateTime The LocalDateTime to truncate
     * @param unit ChronoUnit to truncate to (e.g., ChronoUnit.DAYS)
     * @return Truncated LocalDateTime
     */
    public static LocalDateTime truncate(LocalDateTime dateTime, ChronoUnit unit) {
        if (dateTime == null) {
            return null;
        }
        
        return dateTime.truncatedTo(unit);
    }
    
    /**
     * Get the start of day for a given LocalDateTime
     * 
     * @param dateTime The LocalDateTime
     * @return LocalDateTime at start of day (00:00:00)
     */
    public static LocalDateTime getStartOfDay(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        
        return dateTime.toLocalDate().atStartOfDay();
    }
    
    /**
     * Get the end of day for a given LocalDateTime
     * 
     * @param dateTime The LocalDateTime
     * @return LocalDateTime at end of day (23:59:59.999999999)
     */
    public static LocalDateTime getEndOfDay(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        
        return dateTime.toLocalDate().atTime(23, 59, 59, 999999999);
    }
}