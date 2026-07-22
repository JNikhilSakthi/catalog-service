package com.medha.catalogservice.exception;

public class InvalidCacheNameException extends RuntimeException {

    public InvalidCacheNameException(String cacheName) {
        super("Unknown cache name: " + cacheName);
    }
}
