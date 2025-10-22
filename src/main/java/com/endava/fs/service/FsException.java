package com.endava.fs.service;

import org.springframework.http.HttpStatus;

public class FsException extends RuntimeException {

    private final HttpStatus status;

    public FsException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public FsException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
