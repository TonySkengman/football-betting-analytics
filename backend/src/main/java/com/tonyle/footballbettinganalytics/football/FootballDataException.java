package com.tonyle.footballbettinganalytics.football;

import org.springframework.http.HttpStatus;

public class FootballDataException extends RuntimeException {
    private final HttpStatus status;

    public FootballDataException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() { return status; }
}
