package com.pararepilot.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.pararepilot.repository.DatabaseManager;

public class QuestionImageStorage {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("png", "jpg", "jpeg");
    private static final String IMAGE_DIRECTORY_NAME = "images";

    public String copyIntoImageStore(Path sourcePath) throws IOException {
        if (sourcePath == null) {
            return null;
        }

        if (!Files.isRegularFile(sourcePath)) {
            throw new IOException("Selected image file does not exist.");
        }

        String extension = extension(sourcePath)
                .orElseThrow(() -> new IOException("Image files must be PNG, JPG, or JPEG."));

        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw new IOException("Image files must be PNG, JPG, or JPEG.");
        }

        Path imageDirectory = imageDirectory();
        Files.createDirectories(imageDirectory);

        String fileName = UUID.randomUUID() + "." + extension;
        Path destination = imageDirectory.resolve(fileName);
        Files.copy(sourcePath, destination);

        return IMAGE_DIRECTORY_NAME + "/" + fileName;
    }

    public static Optional<Path> resolveImagePath(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) {
            return Optional.empty();
        }

        Path path = Path.of(storedPath);

        if (path.isAbsolute()) {
            return Optional.of(path.normalize());
        }

        String normalizedPath = storedPath.replace("\\", "/");
        return Optional.of(Path.of(DatabaseManager.DB_DIR).resolve(normalizedPath).normalize());
    }

    public static boolean isSupportedImage(Path path) {
        return extension(path)
                .map(SUPPORTED_EXTENSIONS::contains)
                .orElse(false);
    }

    private static Path imageDirectory() {
        return Path.of(DatabaseManager.DB_DIR).resolve(IMAGE_DIRECTORY_NAME);
    }

    private static Optional<String> extension(Path path) {
        if (path == null || path.getFileName() == null) {
            return Optional.empty();
        }

        String fileName = path.getFileName().toString();
        int extensionStart = fileName.lastIndexOf('.');

        if (extensionStart < 0 || extensionStart == fileName.length() - 1) {
            return Optional.empty();
        }

        return Optional.of(fileName.substring(extensionStart + 1).toLowerCase(Locale.ROOT));
    }
}
