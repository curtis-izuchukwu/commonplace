package com.commonplace.service;

import com.commonplace.flightdeck.FlightDeckApiClient;
import com.commonplace.flightdeck.FlightDeckApiException;
import com.commonplace.flightdeck.FlightDeckGenerateRequest;
import com.commonplace.flightdeck.FlightDeckGenerateResponse;

public class FlightDeckWorksheetGenerationService {

    private final FlightDeckApiClient client;

    public FlightDeckWorksheetGenerationService() {
        this(new FlightDeckApiClient());
    }

    public FlightDeckWorksheetGenerationService(FlightDeckApiClient client) {
        this.client = client;
    }

    public FlightDeckGenerateResponse generate(FlightDeckGenerateRequest request)
            throws FlightDeckApiException {
        return client.generate(request);
    }
}
