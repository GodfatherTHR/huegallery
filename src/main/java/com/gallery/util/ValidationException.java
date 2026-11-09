package com.gallery.util;

/** Signals a user-correctable validation problem; the message is safe to display. */
public class ValidationException extends RuntimeException {

    public ValidationException(String message) {
        super(message);
    }
}
