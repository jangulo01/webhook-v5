package com.sg.webhookservice.aspect;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Aspecto para auditoría de seguridad que:
 * - Registra intentos de acceso a recursos protegidos
 * - Realiza seguimiento de cambios en configuraciones de webhook
 * - Audita operaciones administrativas
 * - Mantiene logs de eventos de seguridad relevantes
 */
@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class SecurityAuditAspect {
    
    @Value("${security.audit.enabled:true}")
    private boolean auditEnabled;
    
    // Nombre del log específico para auditoría
    private static final String AUDIT_LOGGER = "SECURITY_AUDIT";
    
    /**
     * Audita accesos a endpoints administrativos
     */
    @Before("execution(* com.yourcompany.webhookservice.controller.AdminController.*(..))")
    public void auditAdminAccess(JoinPoint joinPoint) {
        if (!auditEnabled) return;
        
        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
        String methodName = methodSignature.getName();
        String username = getCurrentUsername();
        String clientIp = getClientIp();
        
        log.info("{} | ADMIN_ACCESS | User: {} | IP: {} | Method: {} | Args: {}", 
                AUDIT_LOGGER, username, clientIp, methodName, 
                formatMethodArgs(joinPoint.getArgs()));
    }
    
    /**
     * Audita operaciones de creación o modificación de webhooks
     */
    @AfterReturning(
        pointcut = "execution(* com.yourcompany.webhookservice.service.WebhookService.create*(..)) || " +
                  "execution(* com.yourcompany.webhookservice.service.WebhookService.update*(..)) || " +
                  "execution(* com.yourcompany.webhookservice.service.WebhookService.delete*(..))",
        returning = "result"
    )
    public void auditWebhookConfigChange(JoinPoint joinPoint, Object result) {
        if (!auditEnabled) return;
        
        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
        String methodName = methodSignature.getName();
        String username = getCurrentUsername();
        String clientIp = getClientIp();
        
        // Determinar tipo de operación
        String operationType = "UNKNOWN";
        if (methodName.startsWith("create")) {
            operationType = "CREATE";
        } else if (methodName.startsWith("update")) {
            operationType = "UPDATE";
        } else if (methodName.startsWith("delete")) {
            operationType = "DELETE";
        }
        
        // Extraer identificador del webhook (nombre o ID) del resultado o argumentos
        String webhookIdentifier = extractWebhookIdentifier(result, joinPoint.getArgs());
        
        log.info("{} | WEBHOOK_CONFIG_{} | User: {} | IP: {} | Webhook: {}", 
                AUDIT_LOGGER, operationType, username, clientIp, webhookIdentifier);
    }
    
    /**
     * Audita fallos de autenticación
     */
    @AfterThrowing(
        pointcut = "execution(* com.yourcompany.webhookservice.security.service.*Service.authenticate(..))",
        throwing = "ex"
    )
    public void auditAuthenticationFailure(JoinPoint joinPoint, Exception ex) {
        if (!auditEnabled) return;
        
        String clientIp = getClientIp();
        String username = "unknown";
        
        // Intentar extraer username de los argumentos
        if (joinPoint.getArgs().length > 0 && joinPoint.getArgs()[0] != null) {
            if (joinPoint.getArgs()[0] instanceof String) {
                username = (String) joinPoint.getArgs()[0];
            } else {
                // Si es un objeto de request, intentar extraer el username
                try {
                    Object requestObj = joinPoint.getArgs()[0];
                    username = requestObj.toString();
                } catch (Exception e) {
                    // Ignorar errores al extraer username
                }
            }
        }
        
        log.warn("{} | AUTH_FAILURE | User: {} | IP: {} | Reason: {}", 
                AUDIT_LOGGER, username, clientIp, ex.getMessage());
    }
    
    /**
     * Audita verificaciones de firma HMAC
     */
    @AfterReturning(
        pointcut = "execution(* com.yourcompany.webhookservice.service.HmacService.verifySignature(..))",
        returning = "result"
    )
    public void auditSignatureVerification(JoinPoint joinPoint, boolean result) {
        if (!auditEnabled) return;
        
        // Solo auditar fallos de verificación
        if (!result) {
            String webhookName = "unknown";
            
            // Intentar extraer nombre del webhook si está disponible
            if (joinPoint.getArgs().length > 2 && joinPoint.getArgs()[2] != null) {
                webhookName = joinPoint.getArgs()[2].toString();
            }
            
            log.warn("{} | SIGNATURE_VERIFICATION_FAILED | Webhook: {} | Time: {}", 
                    AUDIT_LOGGER, webhookName, LocalDateTime.now());
        }
    }
    
    /**
     * Obtiene el nombre de usuario actual del contexto de seguridad
     */
    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : "anonymous";
    }
    
    /**
     * Obtiene la dirección IP del cliente
     */
    private String getClientIp() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                
                // Verificar headers de proxy
                String xForwardedFor = request.getHeader("X-Forwarded-For");
                if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                    // X-Forwarded-For puede tener múltiples IPs, tomar la primera (cliente original)
                    return xForwardedFor.split(",")[0].trim();
                }
                
                return request.getRemoteAddr();
            }
        } catch (Exception e) {
            log.debug("Error getting client IP: {}", e.getMessage());
        }
        return "unknown";
    }
    
    /**
     * Formatea argumentos del método para auditoría
     */
    private String formatMethodArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return "[]";
        }
        
        return Arrays.stream(args)
                .map(arg -> arg == null ? "null" : arg.toString())
                .map(this::sanitizeForLogging)
                .collect(Collectors.joining(", ", "[", "]"));
    }
    
    /**
     * Sanea datos sensibles para logging
     */
    private String sanitizeForLogging(String input) {
        if (input == null) {
            return "null";
        }
        
        // Truncar strings largos
        if (input.length() > 100) {
            input = input.substring(0, 97) + "...";
        }
        
        // Eliminar datos sensibles como contraseñas o tokens
        if (input.contains("password") || input.contains("secret") || 
            input.contains("token") || input.contains("key")) {
            input = "[REDACTED]";
        }
        
        return input;
    }
    
    /**
     * Extrae identificador de webhook del resultado o argumentos
     */
    private String extractWebhookIdentifier(Object result, Object[] args) {
        // Primero intentar extraer del resultado
        if (result != null) {
            try {
                // Si resultado tiene método getId o getName, usarlo
                return result.toString();
            } catch (Exception e) {
                // Ignorar errores
            }
        }
        
        // Si no, intentar extraer del primer argumento
        if (args != null && args.length > 0 && args[0] != null) {
            return sanitizeForLogging(args[0].toString());
        }
        
        return "unknown";
    }
}