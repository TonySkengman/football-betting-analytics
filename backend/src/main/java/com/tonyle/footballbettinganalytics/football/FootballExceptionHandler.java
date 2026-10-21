package com.tonyle.footballbettinganalytics.football;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class FootballExceptionHandler {
    public record ApiError(String message) {}

    @ExceptionHandler(FootballDataException.class)
    public ResponseEntity<ApiError> handle(FootballDataException exception) {
        return ResponseEntity.status(exception.status()).body(new ApiError(exception.getMessage()));
    }
}
