package com.hananimed.service;

/** Thrown when a request breaks a clinic business rule (duplicate ID, double booking, etc.). */
public class ClinicException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ClinicException(String message) {
        super(message);
    }
}
