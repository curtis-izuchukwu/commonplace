package com.commonplace.importer;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

@EnabledOnOs(OS.WINDOWS)
class WindowsOcrServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void extractsTextWithWindowsOcrWhenAvailable() throws Exception {
        WindowsOcrService service = new WindowsOcrService();
        assumeTrue(service.isAvailable());

        Path imagePath = tempDir.resolve("ocr-text.png");
        BufferedImage image = new BufferedImage(1000, 220, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();

        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.setColor(Color.BLACK);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 52));
            graphics.drawString("Question 1. Define binary search.", 24, 120);
        } finally {
            graphics.dispose();
        }

        ImageIO.write(image, "png", imagePath.toFile());

        OcrResult result = service.extractTextFromImage(imagePath);

        assertTrue(
                result.text().toLowerCase().contains("question"),
                "OCR text was: " + result.text()
        );
    }
}
