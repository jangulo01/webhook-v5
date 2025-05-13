package com.sg.webhookservice.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Aspecto para logging centralizado que permite:
 * - Registrar todas las llamadas a controladores REST
 * - Registrar las excepciones de los servicios
 * - Rastrear el tiempo de ejecución de operaciones
 * - Generar IDs de correlación para seguimiento de solicitudes
 */
@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class LoggingAspect {
    
    private final ObjectMapper objectMapper;
    
    @Value("${logging.verbose:false}")
    private boolean verboseLogging;
    
    /**
     * Pointcut para todos los métodos en controladores
     */
    @Pointcut("execution(* com.sg.webhookservice.controller.*.*(..))")
    public void controllerMethods() {}
    
    /**
     * Pointcut para todos los métodos en servicios
     */
    @Pointcut("execution(* com.sg.webhookservice.service.*.*(..))")
    public void serviceMethods() {}
    
    /**
     * Pointcut para métodos críticos relacionados con webhooks
     */
    @Pointcut("execution(* com.sg.webhookservice.service.*Webhook*.*(..)) || " +
              "execution(* com.sg.webhookservice.service.*Message*.*(..)) || " +
              "execution(* com.sg.webhookservice.service.HmacService.*(..))")
    public void criticalWebhookMethods() {}
    
    /**
     * Registra entrada/salida para métodos de controladores REST
     */
    @Around("controllerMethods()")
    public Object logAroundControllers(ProceedingJoinPoint joinPoint) throws Throwable {
        String requestId = generateRequestId();
        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
        String className = methodSignature.getDeclaringType().getSimpleName();
        String methodName = methodSignature.getName();
        
        // Log para la solicitud entrante
        logIncomingRequest(joinPoint, requestId, className, methodName);
        
        long startTime = System.currentTimeMillis();
        Object result = null;
        
        try {
            // Procede con la ejecución del método
            result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - startTime;
            
            // Log para la respuesta
            log.info("RequestId: {} | Response from {}.{} completed in {} ms", 
                    requestId, className, methodName, duration);
            
            if (verboseLogging && result != null) {
                try {
                    // Solo loguear primeros 500 caracteres para evitar logs excesivos
                    String resultStr = objectMapper.writeValueAsString(result);
                    if (resultStr.length() > 500) {
                        resultStr = resultStr.substring(0, 500) + "...";
                    }
                    log.debug("RequestId: {} | Response body: {}", requestId, resultStr);
                } catch (Exception e) {
                    log.debug("RequestId: {} | Could not serialize response", requestId);
                }
            }
            
            return result;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("RequestId: {} | Exception from {}.{} after {} ms: {} - {}", 
                    requestId, className, methodName, duration, 
                    e.getClass().getSimpleName(), e.getMessage());
            throw e;
        }
    }
    
    /**
     * Registra tiempos para métodos críticos de webhook
     */
    @Around("criticalWebhookMethods()")
    public Object logAroundCriticalWebhookMethods(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
        String className = methodSignature.getDeclaringType().getSimpleName();
        String methodName = methodSignature.getName();
        
        long startTime = System.currentTimeMillis();
        try {
            // Log resumido para métodos altamente invocados
            if (log.isDebugEnabled()) {
                log.debug("Executing {}.{} with args: {}", 
                        className, methodName, summarizeArgs(joinPoint.getArgs()));
            }
            return joinPoint.proceed();
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            if (duration > 500) { // Solo log para ejecuciones lentas
                log.warn("{}.{} execution took {} ms", className, methodName, duration);
            } else if (log.isDebugEnabled()) {
                log.debug("{}.{} execution completed in {} ms", className, methodName, duration);
            }
        }
    }
    
    /**
     * Log para excepciones lanzadas en cualquier servicio
     */
    @AfterThrowing(pointcut = "serviceMethods()", throwing = "ex")
    public void logServiceException(JoinPoint joinPoint, Exception ex) {
        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
        String className = methodSignature.getDeclaringType().getSimpleName();
        String methodName = methodSignature.getName();
        
        log.error("Exception in {}.{} - Exception: {} - Message: {}",
                className, methodName, ex.getClass().getSimpleName(), ex.getMessage(), ex);
    }
    
    /**
     * Genera un ID único para seguimiento de solicitudes
     */
    private String generateRequestId() {
        return UUID.randomUUID().toString();
    }
    
    /**
     * Registra detalles de la solicitud entrante
     */
    private void logIncomingRequest(JoinPoint joinPoint, String requestId, String className, String methodName) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            
            // Registrar detalles de la solicitud HTTP
            log.info("RequestId: {} | Incoming request to {}.{} | URL: {} {} | Client IP: {}",
                    requestId,
                    className,
                    methodName,
                    request.getMethod(),
                    request.getRequestURI(),
                    request.getRemoteAddr());
            
            // Log detallado si está habilitado
            if (verboseLogging) {
                // Loguear headers
                Map<String, String> headers = new LinkedHashMap<>();
                request.getHeaderNames().asIterator().forEachRemaining(headerName -> 
                    headers.put(headerName, request.getHeader(headerName))
                );
                
                log.debug("RequestId: {} | Headers: {}", requestId, headers);
                
                // Loguear cuerpo de la solicitud para métodos POST/PUT
                if (joinPoint.getArgs().length > 0 && 
                    (request.getMethod().equals("POST") || request.getMethod().equals("PUT"))) {
                    try {
                        String body = objectMapper.writeValueAsString(joinPoint.getArgs()[0]);
                        // Limitar tamaño del cuerpo en el log
                        if (body.length() > 500) {
                            body = body.substring(0, 500) + "...";
                        }
                        log.debug("RequestId: {} | Request body: {}", requestId, body);
                    } catch (Exception e) {
                        log.debug("RequestId: {} | Could not serialize request body", requestId);
                    }
                }
            }
        } else {
            // Para métodos que no son solicitudes HTTP
            log.debug("Method call to {}.{} with arguments: {}",
                    className,
                    methodName,
                    summarizeArgs(joinPoint.getArgs()));
        }
    }
    
    /**
     * Crea un resumen de los argumentos para el log
     */
    private String summarizeArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return "[]";
        }
        
        StringBuilder summary = new StringBuilder("[");
        for (int i = 0; i < args.length; i++) {
            if (i > 0) {
                summary.append(", ");
            }
            
            if (args[i] == null) {
                summary.append("null");
            } else if (args[i] instanceof String) {
                String arg = (String) args[i];
                summary.append("\"")
                      .append(arg.length() > 50 ? arg.substring(0, 47) + "..." : arg)
                      .append("\"");
            } else {
                summary.append(args[i].getClass().getSimpleName())
                      .append("@")
                      .append(Integer.toHexString(args[i].hashCode()));
            }
        }
        summary.append("]");
        return summary.toString();
    }
}