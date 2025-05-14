// DeliveryAttemptDto.java
package com.sg.webhookservice.dto;

import java.util.UUID;

import lombok.Data;

/**
 * DTO para la información de intentos de entrega de webhook
 */
@Data
public class DeliveryAttemptDto {

    private UUID id;
    private Integer attemptNumber;
    private String timestamp;
    private Integer statusCode;
    private String responseBody;
    private String error;
    private Long requestDuration;
    private String targetUrl;
    private String responseHeaders;
    private String processingNode;
}