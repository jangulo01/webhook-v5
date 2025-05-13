package com.sg.webhookservice.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.ConversionNotSupportedException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Manejador global de excepciones para toda la aplicación.
 * 
 * Captura todas las excepciones lanzadas durante el procesamiento de solicitudes
 * y las convierte en respuestas de error estandarizadas. Proporciona manejo
 * específico para diferentes tipos de excepciones y un mecanismo de fallback
 * para excepciones no manejadas explícitamente.
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    
    private final Environment environment;
    
    /**
     * Determina si estamos en un entorno de desarrollo
     */
    private boolean isDevEnvironment() {
        String[] activeProfiles = environment.getActiveProfiles();
        return Arrays.asList(activeProfiles).contains("dev") || 
               Arrays.asList(activeProfiles).contains("local");
    }
    
    /**
     * Genera un identificador de rastreo único para correlacionar con logs
     */
    private String generateTraceId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
    
    /**
     * Crea una respuesta de error a partir de una excepción API personalizada
     */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Object> handleApiException(ApiException ex, HttpServletRequest request) {
        String traceId = generateTraceId();
        
        // Registro detallado del error
        log.error("API Exception [{}]: {} at path: {}", 
                traceId, ex.getMessage(), request.getRequestURI(), ex);
        
        ErrorResponse errorResponse = ErrorResponse.fromException(ex, request.getRequestURI())
                .setTraceId(traceId);
        
        // Agregar información técnica en entorno de desarrollo
        if (isDevEnvironment() && ex.getCause() != null) {
            errorResponse.setDebugInfo(ex.getCause().toString());
        }
        
        return new ResponseEntity<>(errorResponse, ex.getStatus());
    }
    
    /**
     * Maneja excepciones de credenciales inválidas (autenticación)
     */
    @ExceptionHandler(com.yourcompany.webhookservice.exception.BadCredentialsException.class)
    public ResponseEntity<Object> handleBadCredentials(
            com.yourcompany.webhookservice.exception.BadCredentialsException ex, 
            HttpServletRequest request) {
        
        String traceId = generateTraceId();
        
        // Registro del intento fallido de autenticación
        log.warn("Authentication failure [{}]: {} at path: {}", 
                traceId, ex.getMessage(), request.getRequestURI());
        
        ErrorResponse errorResponse = ErrorResponse.fromException(ex, request.getRequestURI())
                .setTraceId(traceId);
        
        return new ResponseEntity<>(errorResponse, ex.getStatus());
    }
    
    /**
     * Maneja excepciones de acceso denegado (autorización)
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {
        
        String traceId = generateTraceId();
        
        // Registro del intento de acceso no autorizado
        log.warn("Access denied [{}]: {} at path: {}", 
                traceId, ex.getMessage(), request.getRequestURI());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode("ACCESS_DENIED")
                .message("No tiene permisos suficientes para realizar esta operación")
                .status(HttpStatus.FORBIDDEN.value())
                .timestamp(LocalDateTime.now())
                .path(request.getRequestURI())
                .traceId(traceId)
                .build();
        
        return new ResponseEntity<>(errorResponse, HttpStatus.FORBIDDEN);
    }
    
    /**
     * Maneja excepciones de credenciales inválidas de Spring Security
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Object> handleSpringBadCredentials(
            BadCredentialsException ex, HttpServletRequest request) {
        
        String traceId = generateTraceId();
        
        // Registro del intento fallido de autenticación
        log.warn("Spring authentication failure [{}]: {} at path: {}", 
                traceId, ex.getMessage(), request.getRequestURI());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode("INVALID_CREDENTIALS")
                .message("Credenciales de autenticación inválidas")
                .status(HttpStatus.UNAUTHORIZED.value())
                .timestamp(LocalDateTime.now())
                .path(request.getRequestURI())
                .traceId(traceId)
                .build();
        
        return new ResponseEntity<>(errorResponse, HttpStatus.UNAUTHORIZED);
    }
    
    /**
     * Maneja errores de base de datos
     */
    @ExceptionHandler({DataAccessException.class, SQLException.class})
    public ResponseEntity<Object> handleDatabaseException(Exception ex, HttpServletRequest request) {
        String traceId = generateTraceId();
        
        // Registro detallado del error de base de datos
        log.error("Database error [{}]: {} at path: {}", 
                traceId, ex.getMessage(), request.getRequestURI(), ex);
        
        // Crear respuesta de error sin exponer detalles técnicos al usuario
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode("DATABASE_ERROR")
                .message("Error en operación de base de datos")
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .timestamp(LocalDateTime.now())
                .path(request.getRequestURI())
                .traceId(traceId)
                .build();
        
        // Agregar información técnica en entorno de desarrollo
        if (isDevEnvironment()) {
            errorResponse.setDebugInfo(ex.toString());
        }
        
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }
    
    /**
     * Maneja errores de integridad de datos (violaciones de restricciones)
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> handleDataIntegrityViolation(
            DataIntegrityViolationException ex, HttpServletRequest request) {
        
        String traceId = generateTraceId();
        
        // Registro del error de integridad
        log.error("Data integrity violation [{}]: {} at path: {}", 
                traceId, ex.getMessage(), request.getRequestURI(), ex);
        
        // Determinar si es un error de duplicado (restricción única)
        boolean isDuplicateKey = ex.getMessage() != null && 
                (ex.getMessage().contains("duplicate key") || 
                 ex.getMessage().contains("Duplicate entry") ||
                 ex.getMessage().contains("unique constraint") ||
                 ex.getMessage().contains("violates unique constraint"));
        
        // Crear respuesta específica según el tipo de error
        ErrorResponse errorResponse;
        if (isDuplicateKey) {
            errorResponse = ErrorResponse.builder()
                    .errorCode("DUPLICATE_RESOURCE")
                    .message("El recurso ya existe con ese identificador")
                    .status(HttpStatus.CONFLICT.value())
                    .timestamp(LocalDateTime.now())
                    .path(request.getRequestURI())
                    .traceId(traceId)
                    .build();
            
            return new ResponseEntity<>(errorResponse, HttpStatus.CONFLICT);
        } else {
            errorResponse = ErrorResponse.builder()
                    .errorCode("DATA_INTEGRITY_VIOLATION")
                    .message("Violación de integridad de datos")
                    .status(HttpStatus.BAD_REQUEST.value())
                    .timestamp(LocalDateTime.now())
                    .path(request.getRequestURI())
                    .traceId(traceId)
                    .build();
            
            // Agregar información técnica en entorno de desarrollo
            if (isDevEnvironment()) {
                errorResponse.setDebugInfo(ex.getMostSpecificCause().toString());
            }
            
            return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
        }
    }
    
    /**
     * Maneja errores de validación de entidades
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {
        
        String traceId = generateTraceId();
        
        // Registro del error de validación
        log.warn("Constraint violation [{}]: {} at path: {}", 
                traceId, ex.getMessage(), request.getRequestURI());
        
        // Extraer detalles de las violaciones de restricciones
        List<ErrorResponse.ValidationError> validationErrors = ex.getConstraintViolations()
                .stream()
                .map(this::buildValidationError)
                .collect(Collectors.toList());
        
        ErrorResponse errorResponse = ErrorResponse.validationError(
                "Error de validación",
                request.getRequestURI(),
                validationErrors
        ).setTraceId(traceId);
        
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }
    
    /**
     * Maneja errores de tipo en argumentos de método
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Object> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        
        String traceId = generateTraceId();
        
        // Registro del error de tipo
        log.warn("Type mismatch [{}]: {} at path: {}", 
                traceId, ex.getMessage(), request.getRequestURI());
        
        String paramName = ex.getName();
        String requiredType = ex.getRequiredType() != null ? 
                ex.getRequiredType().getSimpleName() : "desconocido";
        
        String errorMessage = String.format(
                "El parámetro '%s' debe ser de tipo %s", paramName, requiredType);
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode("TYPE_MISMATCH")
                .message(errorMessage)
                .status(HttpStatus.BAD_REQUEST.value())
                .timestamp(LocalDateTime.now())
                .path(request.getRequestURI())
                .traceId(traceId)
                .build();
        
        // Agregar detalle de validación
        List<ErrorResponse.ValidationError> validationErrors = new ArrayList<>();
        validationErrors.add(new ErrorResponse.ValidationError(
                paramName,
                errorMessage,
                String.valueOf(ex.getValue()),
                "TYPE_MISMATCH"
        ));
        errorResponse.setErrors(validationErrors);
        
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }
    
    /**
     * Maneja errores de validación en argumentos de método
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, 
            HttpHeaders headers, 
            HttpStatusCode status, 
            WebRequest request) {
        
        String traceId = generateTraceId();
        String path = ((ServletWebRequest) request).getRequest().getRequestURI();
        
        // Registro del error de validación
        log.warn("Validation error [{}]: {} field errors at path: {}", 
                traceId, ex.getBindingResult().getFieldErrorCount(), path);
        
        // Extraer detalles de los errores de campo
        List<ErrorResponse.ValidationError> validationErrors = ex.getBindingResult().getFieldErrors()
                .stream()
                .map(this::buildValidationError)
                .collect(Collectors.toList());
        
        ErrorResponse errorResponse = ErrorResponse.validationError(
                "Error de validación",
                path,
                validationErrors
        ).setTraceId(traceId);
        
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }
    
    /**
     * Maneja errores de deserialización de JSON
     */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, 
            HttpHeaders headers, 
            HttpStatusCode status, 
            WebRequest request) {
        
        String traceId = generateTraceId();
        String path = ((ServletWebRequest) request).getRequest().getRequestURI();
        
        // Registro del error de deserialización
        log.warn("Message not readable [{}]: {} at path: {}", 
                traceId, ex.getMessage(), path);
        
        String errorMessage = "Error al procesar el cuerpo de la solicitud JSON";
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode("INVALID_JSON")
                .message(errorMessage)
                .status(HttpStatus.BAD_REQUEST.value())
                .timestamp(LocalDateTime.now())
                .path(path)
                .traceId(traceId)
                .build();
        
        // Agregar información técnica en entorno de desarrollo
        if (isDevEnvironment()) {
            errorResponse.setDebugInfo(ex.getMessage());
        }
        
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }
    
    /**
     * Maneja errores de solicitud HTTP no encontrada
     */
    @Override
    protected ResponseEntity<Object> handleNoHandlerFoundException(
            NoHandlerFoundException ex, 
            HttpHeaders headers, 
            HttpStatusCode status, 
            WebRequest request) {
        
        String traceId = generateTraceId();
        String path = ((ServletWebRequest) request).getRequest().getRequestURI();
        
        // Registro del error de ruta no encontrada
        log.warn("No handler found [{}]: {} {} at path: {}", 
                traceId, ex.getHttpMethod(), ex.getRequestURL(), path);
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode("ENDPOINT_NOT_FOUND")
                .message(String.format("No se encontró el endpoint %s %s", 
                        ex.getHttpMethod(), ex.getRequestURL()))
                .status(HttpStatus.NOT_FOUND.value())
                .timestamp(LocalDateTime.now())
                .path(path)
                .traceId(traceId)
                .build();
        
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }
    
    /**
     * Maneja errores de método HTTP no soportado
     */
    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, 
            HttpHeaders headers, 
            HttpStatusCode status, 
            WebRequest request) {
        
        String traceId = generateTraceId();
        String path = ((ServletWebRequest) request).getRequest().getRequestURI();
        
        // Registro del error de método no soportado
        log.warn("Method not supported [{}]: {} at path: {}", 
                traceId, ex.getMessage(), path);
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode("METHOD_NOT_ALLOWED")
                .message(String.format("El método %s no está soportado para esta ruta", 
                        ex.getMethod()))
                .status(HttpStatus.METHOD_NOT_ALLOWED.value())
                .timestamp(LocalDateTime.now())
                .path(path)
                .traceId(traceId)
                .build();
        
        return new ResponseEntity<>(errorResponse, HttpStatus.METHOD_NOT_ALLOWED);
    }
    
    /**
     * Maneja cualquier excepción no capturada específicamente
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUncaughtException(Exception ex, HttpServletRequest request) {
        String traceId = generateTraceId();
        
        // Registro detallado del error no esperado
        log.error("Uncaught exception [{}]: {} at path: {}", 
                traceId, ex.getMessage(), request.getRequestURI(), ex);
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode("INTERNAL_SERVER_ERROR")
                .message("Ha ocurrido un error interno del servidor")
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .timestamp(LocalDateTime.now())
                .path(request.getRequestURI())
                .traceId(traceId)
                .build();
        
        // Agregar información técnica en entorno de desarrollo
        if (isDevEnvironment()) {
            errorResponse.setDebugInfo(ex.toString());
            
            // Incluir información de la causa raíz si está disponible
            if (ex.getCause() != null) {
                errorResponse.setDebugInfo(errorResponse.getDebugInfo() + 
                        " | Caused by: " + ex.getCause().toString());
            }
        }
        
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }
    
    /**
     * Convierte un error de validación de Bean Validation a nuestro formato estándar
     */
    private ErrorResponse.ValidationError buildValidationError(FieldError fieldError) {
        return new ErrorResponse.ValidationError(
                fieldError.getField(),
                fieldError.getDefaultMessage(),
                fieldError.getRejectedValue() != null ? fieldError.getRejectedValue().toString() : null,
                fieldError.getCode()
        );
    }
    
    /**
     * Convierte una violación de restricción a nuestro formato estándar
     */
    private ErrorResponse.ValidationError buildValidationError(ConstraintViolation<?> violation) {
        // Extraer el nombre del campo a partir del path de la propiedad
        String propertyPath = violation.getPropertyPath().toString();
        String fieldName = propertyPath.contains(".") ? 
                propertyPath.substring(propertyPath.lastIndexOf('.') + 1) : propertyPath;
        
        return new ErrorResponse.ValidationError(
                fieldName,
                violation.getMessage(),
                violation.getInvalidValue() != null ? violation.getInvalidValue().toString() : null,
                violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName()
        );
    }
}