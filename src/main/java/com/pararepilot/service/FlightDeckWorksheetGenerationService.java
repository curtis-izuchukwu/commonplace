package com.pararepilot.service;

import com.pararepilot.flightdeck.FlightDeckApiClient;
import com.pararepilot.flightdeck.FlightDeckApiException;
import com.pararepilot.flightdeck.FlightDeckGenerateRequest;
import com.pararepilot.flightdeck.FlightDeckGenerateResponse;

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
