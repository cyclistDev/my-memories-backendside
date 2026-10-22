package com.group5.MyMemories.exception;

import com.group5.MyMemories.dto.response.ApiErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.TreeMap;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> fieldErrors = new TreeMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));

        return errorResponse(HttpStatus.BAD_REQUEST, "Validation failed", fieldErrors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpMessageNotReadableException exception) {
        return errorResponse(HttpStatus.BAD_REQUEST, "Malformed or missing request body", Map.of());
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApiException(ApiException exception) {
        return errorResponse(exception.getStatus(), exception.getMessage(), exception.getFieldErrors());
    }

    private ResponseEntity<ApiErrorResponse> errorResponse(
            HttpStatus status, String message, Map<String, String> fieldErrors) {
        return ResponseEntity.status(status)
                .body(new ApiErrorResponse(status.value(), message, fieldErrors));
    }
}
