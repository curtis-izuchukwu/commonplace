package com.commonplace.press;

public class PressApiException extends Exception {

    public PressApiException(String message) {
        super(message);
    }

    public PressApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
