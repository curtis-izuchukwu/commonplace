package com.commonplace.service;

import com.commonplace.press.PressApiClient;
import com.commonplace.press.PressApiException;
import com.commonplace.press.PressGenerateRequest;
import com.commonplace.press.PressGenerateResponse;

public class PressWorksheetGenerationService {

    private final PressApiClient client;

    public PressWorksheetGenerationService() {
        // Resolve optional online configuration only when generating, not when opening
        // the offline worksheet editor. A bad URL must not prevent manual creation.
        this(null);
    }

    public PressWorksheetGenerationService(PressApiClient client) {
        this.client = client;
    }

    public PressGenerateResponse generate(PressGenerateRequest request)
            throws PressApiException {
        try {
            return (client == null ? new PressApiClient() : client).generate(request);
        } catch (IllegalArgumentException e) {
            throw new PressApiException("Check the Press API configuration: " + e.getMessage(), e);
        }
    }
}
