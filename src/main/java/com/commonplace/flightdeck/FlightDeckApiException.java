package com.commonplace.flightdeck;

public class FlightDeckApiException extends Exception {

    public FlightDeckApiException(String message) {
        super(message);
    }

    public FlightDeckApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
