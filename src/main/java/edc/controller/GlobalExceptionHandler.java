package edc.controller;

import edc.exception.*;
import edc.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("status", HttpStatus.BAD_REQUEST.value());
        response.put("error", "Validation Error");
        response.put("message", "Invalid input data");
        response.put("errors", errors);

        log.error("Validation error: {}", errors);
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatchException(MethodArgumentTypeMismatchException ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("status", HttpStatus.BAD_REQUEST.value());
        response.put("error", "Type Mismatch Error");
        response.put("message", "Parameter '" + ex.getName() + "' should be of type " + ex.getRequiredType().getSimpleName());

        log.error("Type mismatch error for parameter '{}': expected {}, got {}", 
                 ex.getName(), ex.getRequiredType().getSimpleName(), ex.getValue());
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoHandlerFoundException(NoHandlerFoundException ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("status", HttpStatus.NOT_FOUND.value());
        response.put("error", "Not Found");
        response.put("message", "No handler found for " + ex.getHttpMethod() + " " + ex.getRequestURL());
        response.put("path", ex.getRequestURL());

        log.error("No handler found for {} {}", ex.getHttpMethod(), ex.getRequestURL());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(ParticipantNotActiveException.class)
    public ResponseEntity<ErrorResponse> handleParticipantNotActiveException(ParticipantNotFoundException ex) {
        ErrorResponse response = new ErrorResponse();
        response.setError("NOT_ACTIVE");
        response.setMessage(ex.getMessage());
        response.setStatus(HttpStatus.BAD_REQUEST.value());

        log.error("Participant not active: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(ParticipantNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleParticipantNotFoundException(ParticipantNotFoundException ex) {
        ErrorResponse response = new ErrorResponse();
        response.setError("NOT_FOUND");
        response.setMessage(ex.getMessage());
        response.setStatus(HttpStatus.NOT_FOUND.value());

        log.error("Participant not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(ParticipantConflictException.class)
    public ResponseEntity<ErrorResponse> handleParticipantConflictException(ParticipantConflictException ex) {
        ErrorResponse response = new ErrorResponse();
        response.setError("CONFLICT");
        response.setMessage(ex.getMessage());
        response.setStatus(HttpStatus.CONFLICT.value());

        log.error("Participant conflict: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(TenantNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleTenantNotFoundException(TenantNotFoundException ex) {
        ErrorResponse response = new ErrorResponse();
        response.setError("NOT_FOUND");
        response.setMessage(ex.getMessage());
        response.setStatus(HttpStatus.NOT_FOUND.value());

        log.error("Tenant not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(TenantConflictException.class)
    public ResponseEntity<ErrorResponse> handleTenantConflictException(TenantConflictException ex) {
        ErrorResponse response = new ErrorResponse();
        response.setError("CONFLICT");
        response.setMessage(ex.getMessage());
        response.setStatus(HttpStatus.CONFLICT.value());

        log.error("Tenant conflict: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }


    @ExceptionHandler(CredentialNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCredentialNotFoundException(CredentialNotFoundException ex) {
        ErrorResponse response = new ErrorResponse();
        response.setError("NOT_FOUND");
        response.setMessage(ex.getMessage());
        response.setStatus(HttpStatus.NOT_FOUND.value());

        log.error("Credential not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(ExternalApiException.class)
    public ResponseEntity<ErrorResponse> handleExternalApiException(ExternalApiException ex) {
        ErrorResponse response = new ErrorResponse();
        response.setError("EXTERNAL_API_ERROR");
        response.setMessage(ex.getMessage());
        response.setStatus(HttpStatus.BAD_GATEWAY.value());

        log.error("External API error: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(response);
    }

    @ExceptionHandler(value = {AccessDeniedException.class, AuthorizationDeniedException.class})
    public ResponseEntity<Map<String, Object>> handleAccessDeniedException(org.springframework.security.access.AccessDeniedException ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("status", HttpStatus.FORBIDDEN.value());
        response.put("error", "Forbidden");
        response.put("message", "Access is denied");

        log.error("Access denied: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("status", HttpStatus.BAD_REQUEST.value());
        response.put("error", "Runtime Error");
        response.put("message", ex.getMessage());

        log.error("Runtime error: {}", ex.getMessage(), ex);
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        response.put("error", "Internal Server Error");
        response.put("message", "An unexpected error occurred");

        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
