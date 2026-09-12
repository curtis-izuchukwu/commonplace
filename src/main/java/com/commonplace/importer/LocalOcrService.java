package com.commonplace.importer;

import java.nio.file.Path;
import java.util.List;

public class LocalOcrService implements OcrService {

    private final List<OcrService> services;
    private OcrService availableService;
    private boolean availabilityChecked;

    public LocalOcrService() {
        this(List.of(new TesseractOcrService(), new WindowsOcrService()));
    }

    public LocalOcrService(List<OcrService> services) {
        this.services = services == null ? List.of() : List.copyOf(services);
    }

    @Override
    public boolean isAvailable() {
        return availableService() != null;
    }

    @Override
    public OcrResult extractTextFromImage(Path imagePath) {
        OcrService service = availableService();

        if (service == null) {
            return new NoOpOcrService().extractTextFromImage(imagePath);
        }

        return service.extractTextFromImage(imagePath);
    }

    private OcrService availableService() {
        if (availabilityChecked) {
            return availableService;
        }

        for (OcrService service : services) {
            if (service != null && service.isAvailable()) {
                availableService = service;
                break;
            }
        }

        availabilityChecked = true;
        return availableService;
    }
}
