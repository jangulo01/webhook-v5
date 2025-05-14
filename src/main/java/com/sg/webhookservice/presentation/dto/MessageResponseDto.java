package com.sg.webhookservice.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para respuestas de creación de mensajes.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Respuesta tras la creación de un mensaje de webhook")
public class MessageResponseDto {

    @Schema(description = "ID único del mensaje", example = "123e4567-e89b-12d3-a456-426614174000")
    private String messageId;

    @Schema(description = "Estado del mensaje", example = "pending",
            allowableValues = {"pending", "processing", "delivered", "failed", "cancelled"})
    private String status;

    @Schema(description = "URL para consultar el estado del mensaje")
    private String statusUrl;

    @Schema(description = "Tiempo estimado de entrega (en segundos, solo para mensajes pendientes)", example = "60")
    private Integer estimatedDeliveryTime;
}