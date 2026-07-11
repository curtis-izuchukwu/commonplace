package com.pararepilot.importer;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.contentstream.PDFStreamEngine;
import org.apache.pdfbox.contentstream.operator.DrawObject;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.contentstream.operator.state.Concatenate;
import org.apache.pdfbox.contentstream.operator.state.Restore;
import org.apache.pdfbox.contentstream.operator.state.Save;
import org.apache.pdfbox.contentstream.operator.state.SetGraphicsStateParameters;
import org.apache.pdfbox.contentstream.operator.state.SetMatrix;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.util.Matrix;

import javax.imageio.ImageIO;

import com.pararepilot.service.QuestionImageStorage;

public class PdfImageExtractionService {

    private static final double MIN_RENDERED_IMAGE_WIDTH = 48;
    private static final double MIN_RENDERED_IMAGE_HEIGHT = 48;
    private static final int MIN_INTRINSIC_IMAGE_WIDTH = 48;
    private static final int MIN_INTRINSIC_IMAGE_HEIGHT = 48;
    private static final double MIN_CONTENT_RATIO = 0.003;

    private final QuestionImageStorage imageStorage;

    public PdfImageExtractionService() {
        this(new QuestionImageStorage());
    }

    public PdfImageExtractionService(QuestionImageStorage imageStorage) {
        this.imageStorage = imageStorage;
    }

    public PdfImageExtractionResult extractImages(Path pdfPath, boolean renderPagesForReference) {
        List<ExtractedPdfImage> images = new ArrayList<>();
        List<ImportIssue> issues = new ArrayList<>();

        try (PDDocument document = PDDocument.load(pdfPath.toFile())) {
            PDFRenderer renderer = new PDFRenderer(document);

            for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); pageIndex++) {
                PDPage page = document.getPage(pageIndex);
                int pageNumber = pageIndex + 1;
                int imageCountBeforePage = images.size();

                ImageDrawExtractor extractor = new ImageDrawExtractor(
                        imageStorage,
                        pageNumber,
                        page.getMediaBox().getHeight()
                );
                extractor.processPage(page);
                images.addAll(extractor.images());

                if (extractor.skippedImageCount() > 0) {
                    issues.add(new ImportIssue(
                            ImportIssueSeverity.INFO,
                            "Skipped " + extractor.skippedImageCount()
                                    + " tiny or blank image fragment(s) on page " + pageNumber + "."
                    ));
                }

                if (renderPagesForReference && images.size() == imageCountBeforePage) {
                    BufferedImage renderedPage =
                            renderer.renderImageWithDPI(pageIndex, 144, ImageType.RGB);
                    String imagePath = imageStorage.saveBufferedImage(renderedPage, "png");
                    images.add(new ExtractedPdfImage(
                            imagePath,
                            pageNumber,
                            1,
                            0,
                            "Rendered PDF page " + pageNumber
                    ));
                }
            }

            if (images.isEmpty()) {
                issues.add(new ImportIssue(
                        ImportIssueSeverity.INFO,
                        "No embedded PDF images were found."
                ));
            }

        } catch (IOException e) {
            issues.add(new ImportIssue(
                    ImportIssueSeverity.WARNING,
                    "Images could not be extracted from the PDF: " + e.getMessage()
            ));
        }

        return new PdfImageExtractionResult(images, issues);
    }

    public PdfImageExtractionResult renderPagesForOcr(Path pdfPath) {
        List<ExtractedPdfImage> images = new ArrayList<>();
        List<ImportIssue> issues = new ArrayList<>();

        try (PDDocument document = PDDocument.load(pdfPath.toFile())) {
            PDFRenderer renderer = new PDFRenderer(document);

            for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); pageIndex++) {
                BufferedImage renderedPage =
                        renderer.renderImageWithDPI(pageIndex, 180, ImageType.RGB);
                Path destination = Files.createTempFile("pararepilot-ocr-page-", ".png");
                destination.toFile().deleteOnExit();

                if (!ImageIO.write(renderedPage, "png", destination.toFile())) {
                    throw new IOException("Failed to write OCR page image.");
                }

                int pageNumber = pageIndex + 1;
                images.add(new ExtractedPdfImage(
                        destination.toAbsolutePath().normalize().toString(),
                        pageNumber,
                        1,
                        0,
                        "Rendered PDF page " + pageNumber + " for OCR"
                ));
            }
        } catch (IOException e) {
            issues.add(new ImportIssue(
                    ImportIssueSeverity.WARNING,
                    "PDF pages could not be rendered for OCR: " + e.getMessage()
            ));
        }

        return new PdfImageExtractionResult(images, issues);
    }

    private static final class ImageDrawExtractor extends PDFStreamEngine {

        private final QuestionImageStorage imageStorage;
        private final int pageNumber;
        private final double pageHeight;
        private final List<ExtractedPdfImage> images = new ArrayList<>();
        private int imageIndex;
        private int skippedImageCount;

        private ImageDrawExtractor(
                QuestionImageStorage imageStorage,
                int pageNumber,
                double pageHeight
        ) throws IOException {
            this.imageStorage = imageStorage;
            this.pageNumber = pageNumber;
            this.pageHeight = pageHeight;

            addOperator(new Concatenate());
            addOperator(new DrawObject());
            addOperator(new SetGraphicsStateParameters());
            addOperator(new Save());
            addOperator(new Restore());
            addOperator(new SetMatrix());
        }

        @Override
        protected void processOperator(Operator operator, List<COSBase> operands) throws IOException {
            if (!"Do".equals(operator.getName()) || operands == null || operands.isEmpty()) {
                super.processOperator(operator, operands);
                return;
            }

            COSBase operand = operands.get(0);

            if (!(operand instanceof COSName objectName) || getResources() == null) {
                super.processOperator(operator, operands);
                return;
            }

            PDXObject object = getResources().getXObject(objectName);

            if (object instanceof PDImageXObject imageObject) {
                Matrix transform = getGraphicsState().getCurrentTransformationMatrix();
                BufferedImage image = imageObject.getImage();
                double imageHeight = Math.abs(transform.getScalingFactorY());
                double imageWidth = Math.abs(transform.getScalingFactorX());
                double yPosition = pageHeight - transform.getTranslateY() - imageHeight;

                if (!isMeaningfulImage(image, imageWidth, imageHeight)) {
                    skippedImageCount++;
                    return;
                }

                String imagePath = imageStorage.saveBufferedImage(image, "png");

                images.add(new ExtractedPdfImage(
                        imagePath,
                        pageNumber,
                        ++imageIndex,
                        Math.max(0, yPosition),
                        "PDF image from page " + pageNumber
                ));
            } else if (object instanceof PDFormXObject formObject) {
                showForm(formObject);
            }
        }

        private List<ExtractedPdfImage> images() {
            return images;
        }

        private int skippedImageCount() {
            return skippedImageCount;
        }

        private boolean isMeaningfulImage(
                BufferedImage image,
                double renderedWidth,
                double renderedHeight
        ) {

            if (image == null) {
                return false;
            }

            if (image.getWidth() < MIN_INTRINSIC_IMAGE_WIDTH
                    || image.getHeight() < MIN_INTRINSIC_IMAGE_HEIGHT
                    || renderedWidth < MIN_RENDERED_IMAGE_WIDTH
                    || renderedHeight < MIN_RENDERED_IMAGE_HEIGHT) {
                return false;
            }

            return contentRatio(image) >= MIN_CONTENT_RATIO;
        }

        private double contentRatio(BufferedImage image) {
            int stepX = Math.max(1, image.getWidth() / 80);
            int stepY = Math.max(1, image.getHeight() / 80);
            int sampled = 0;
            int content = 0;

            for (int y = 0; y < image.getHeight(); y += stepY) {
                for (int x = 0; x < image.getWidth(); x += stepX) {
                    sampled++;

                    if (hasVisibleContent(image.getRGB(x, y))) {
                        content++;
                    }
                }
            }

            return sampled == 0 ? 0 : (double) content / sampled;
        }

        private boolean hasVisibleContent(int argb) {
            int alpha = (argb >>> 24) & 0xff;

            if (alpha < 24) {
                return false;
            }

            int red = (argb >>> 16) & 0xff;
            int green = (argb >>> 8) & 0xff;
            int blue = argb & 0xff;
            int max = Math.max(red, Math.max(green, blue));
            int min = Math.min(red, Math.min(green, blue));
            int luminance = (red * 299 + green * 587 + blue * 114) / 1000;

            return luminance < 245 || max - min > 18;
        }
    }
}
