package com.sg.webhookservice.controller;

import com.sg.webhookservice.dto.DeliveryAttemptDto;
import com.sg.webhookservice.dto.MessageDto;
import com.sg.webhookservice.exception.ResourceNotFoundException;
import com.sg.webhookservice.service.MessageProcessingService;
import io.micrometer.core.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Controlador que proporciona endpoints para consultar y gestionar
 * mensajes de webhook individuales.
 */
@RestController
@RequestMapping("/messages")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Messages", description = "Endpoints para consultar y gestionar mensajes de webhook")
@SecurityRequirement(name = "bearerAuth")
public class MessageController {

    private final MessageProcessingService messageProcessingService;

    /**
     * Obtiene un mensaje específico por su ID
     */
    @GetMapping("/{messageId}")
    @Operation(
        summary = "Obtiene un mensaje por ID",
        description = "Recupera detalles completos de un mensaje de webhook por su ID, " +
                      "incluyendo intentos de entrega.",
        responses = {
            @ApiResponse(
                responseCode = "200",
                description = "Mensaje encontrado",
                content = @Content(schema = @Schema(implementation = MessageDto.class))
            ),
            @ApiResponse(responseCode = "404", description = "Mensaje no encontrado")
        }
    )
    @Timed(value = "message.get.byId", description = "Tiempo para obtener mensaje por ID")
    public ResponseEntity<MessageDto> getMessageById(
            @Parameter(description = "ID del mensaje", required = true)
            @PathVariable String messageId) {
        
        try {
            UUID id = UUID.fromString(messageId);
            MessageDto message = messageProcessingService.getMessageById(id);
            return ResponseEntity.ok(message);
        } catch (IllegalArgumentException e) {
            throw new ResourceNotFoundException("Formato de ID inválido: " + messageId);
        }
    }

    /**
     * Busca mensajes con filtros
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    @Operation(
        summary = "Busca mensajes con filtros",
        description = "Recupera mensajes aplicando filtros opcionales como estado, webhook, " +
                      "fechas, etc. Los resultados son paginados."
    )
    @Timed(value = "message.search", description = "Tiempo para buscar mensajes")
    public ResponseEntity<Page<MessageDto>> searchMessages(
            @Parameter(description = "Nombre del webhook")
            @RequestParam(required = false) String webhookName,
            
            @Parameter(description = "Estado del mensaje (PENDING, PROCESSING, DELIVERED, FAILED)")
            @RequestParam(required = false) String status,
            
            @Parameter(description = "Fecha desde (formato ISO: yyyy-MM-ddTHH:mm:ss)")
            @RequestParam(required = false) String fromDate,
            
            @Parameter(description = "Fecha hasta (formato ISO: yyyy-MM-ddTHH:mm:ss)")
            @RequestParam(required = false) String toDate,
            
            @Parameter(description = "Número de página (desde 0)")
            @RequestParam(defaultValue = "0") int page,
            
            @Parameter(description = "Tamaño de página")
            @RequestParam(defaultValue = "20") int size,
            
            @Parameter(description = "Campo de ordenación (created_at, updated_at, status)")
            @RequestParam(defaultValue = "created_at") String sort,
            
            @Parameter(description = "Dirección de ordenación (asc, desc)")
            @RequestParam(defaultValue = "desc") String direction) {
        
        // Validar tamaño de página máximo
        if (size > 100) {
            size = 100;
        }
        
        // Crear objeto de paginación
        Sort.Direction sortDirection = "asc".equalsIgnoreCase(direction) ? 
                Sort.Direction.ASC : Sort.Direction.DESC;
        
        Pageable pageable = PageRequest.of(page, size, sortDirection, sort);
        
        Page<MessageDto> messages = messageProcessingService.searchMessages(
                webhookName, status, fromDate, toDate, pageable);
                
        return ResponseEntity.ok(messages);
    }

    /**
     * Obtiene intentos de entrega para un mensaje
     */
    @GetMapping("/{messageId}/attempts")
    @Operation(
        summary = "Obtiene intentos de entrega para un mensaje",
        description = "Recupera el historial de intentos de entrega para un mensaje específico."
    )
    @Timed(value = "message.get.attempts", description = "Tiempo para obtener intentos de entrega")
    public ResponseEntity<List<DeliveryAttemptDto>> getDeliveryAttempts(
            @Parameter(description = "ID del mensaje", required = true)
            @PathVariable String messageId) {
        
        try {
            UUID id = UUID.fromString(messageId);
            List<DeliveryAttemptDto> attempts = messageProcessingService.getDeliveryAttempts(id);
            return ResponseEntity.ok(attempts);
        } catch (IllegalArgumentException e) {
            throw new ResourceNotFoundException("Formato de ID inválido: " + messageId);
        }
    }

    /**
     * Cancela un mensaje pendiente
     */
    @PostMapping("/{messageId}/cancel")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "Cancela un mensaje pendiente",
        description = "Marca un mensaje pendiente como cancelado para evitar su procesamiento."
    )
    @Timed(value = "message.cancel", description = "Tiempo para cancelar mensaje")
    public ResponseEntity<MessageDto> cancelMessage(
            @Parameter(description = "ID del mensaje", required = true)
            @PathVariable String messageId) {
        
        try {
            UUID id = UUID.fromString(messageId);
            MessageDto result = messageProcessingService.cancelMessage(id);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            throw new ResourceNotFoundException("Formato de ID inválido: " + messageId);
        }
    }

    /**
     * Reintenta un mensaje fallido
     */
    @PostMapping("/{messageId}/retry")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    @Operation(
        summary = "Reintenta un mensaje fallido",
        description = "Programa un reintento inmediato para un mensaje fallido."
    )
    @Timed(value = "message.retry", description = "Tiempo para reintentar mensaje")
    public ResponseEntity<MessageDto> retryMessage(
            @Parameter(description = "ID del mensaje", required = true)
            @PathVariable String messageId,
            
            @Parameter(description = "URL de destino alternativa")
            @RequestParam(required = false) String destinationUrl) {
        
        try {
            UUID id = UUID.fromString(messageId);
            MessageDto result = messageProcessingService.retryMessage(id, destinationUrl);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            throw new ResourceNotFoundException("Formato de ID inválido: " + messageId);
        }
    }

    /**
     * Elimina un mensaje (solo para fines administrativos)
     */
    @DeleteMapping("/{messageId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "Elimina un mensaje",
        description = "Elimina permanentemente un mensaje y sus intentos de entrega asociados. " +
                      "Solo para propósitos administrativos."
    )
    @Timed(value = "message.delete", description = "Tiempo para eliminar mensaje")
    public ResponseEntity<Void> deleteMessage(
            @Parameter(description = "ID del mensaje", required = true)
            @PathVariable String messageId) {
        
        try {
            UUID id = UUID.fromString(messageId);
            messageProcessingService.deleteMessage(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            throw new ResourceNotFoundException("Formato de ID inválido: " + messageId);
        }
    }
}