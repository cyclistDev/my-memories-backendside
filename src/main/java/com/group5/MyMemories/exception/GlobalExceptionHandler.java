package com.group5.MyMemories.exception;

import com.group5.MyMemories.dto.response.ApiErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
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

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingRequestParameter(
            MissingServletRequestParameterException exception) {
        return errorResponse(
                HttpStatus.BAD_REQUEST,
                "Required parameter is missing",
                Map.of(exception.getParameterName(), "Required parameter is missing"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidRequestParameter(
            MethodArgumentTypeMismatchException exception) {
        return errorResponse(
                HttpStatus.BAD_REQUEST,
                "Invalid parameter value",
                Map.of(exception.getName(), "Invalid parameter value"));
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApiException(ApiException exception) {
        return errorResponse(exception.getStatus(), exception.getMessage(), exception.getFieldErrors());
    }

    private ResponseEntity<ApiErrorResponse> errorResponse(
            HttpStatus status, String message, Map<String, String> fieldErrors) {
        return ResponseEntity.status(status)
                .body(new ApiErrorResponse(message, fieldErrors));
    }
}
