package com.commonplace.ui;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** A local, responsive PDF page-spread viewer backed by PDFBox. */
public final class PdfDocumentView extends VBox {

    private static final float RENDER_DPI = 120;
    private static final double MIN_ZOOM = .3;
    private static final double MAX_ZOOM = 2;
    private static final double ZOOM_STEP = .15;
    private static final double SINGLE_PAGE_ZOOM = .75;
    private static final double DEFAULT_RENDERED_PAGE_WIDTH = 992;
    private static final double PAGE_GAP = 18;
    private static final double SURFACE_HORIZONTAL_INSETS = 48;
    private static final int MAX_PAGES_PER_SPREAD = 6;

    private final Button previousButton = new Button("Previous");
    private final Button nextButton = new Button("Next");
    private final Button zoomOutButton = new Button("-");
    private final Button zoomInButton = new Button("+");
    private final Label pageLabel = new Label("Page -");
    private final Label zoomLabel = new Label("80%");
    private final Label statusLabel = new Label();
    private final FlowPane pageSurface = new FlowPane();
    private final ScrollPane scrollPane = new ScrollPane(pageSurface);
    private final List<ImageView> displayedPageImages = new ArrayList<>();

    private Path documentPath;
    private int currentPageIndex;
    private int pageCount;
    private int requestedSpreadSize = 1;
    private double renderedPageWidth = DEFAULT_RENDERED_PAGE_WIDTH;
    private double zoom = .8;
    private long renderRequest;
    private Task<RenderedSpread> renderTask;

    public PdfDocumentView(Path documentPath) {
        setSpacing(8);
        getStyleClass().add("pdf-document-view");

        previousButton.getStyleClass().addAll("compact-button", "pdf-nav-button");
        nextButton.getStyleClass().addAll("compact-button", "pdf-nav-button");
        zoomOutButton.getStyleClass().addAll("compact-button", "pdf-icon-button");
        zoomInButton.getStyleClass().addAll("compact-button", "pdf-icon-button");
        pageLabel.getStyleClass().add("pdf-toolbar-readout");
        zoomLabel.getStyleClass().add("pdf-toolbar-readout");
        statusLabel.getStyleClass().add("status-text");
        statusLabel.setWrapText(true);
        statusLabel.visibleProperty().bind(statusLabel.textProperty().isNotEmpty());
        statusLabel.managedProperty().bind(statusLabel.visibleProperty());

        previousButton.setTooltip(new Tooltip("Previous page spread"));
        nextButton.setTooltip(new Tooltip("Next page spread"));
        zoomOutButton.setTooltip(new Tooltip("Zoom out and show more pages"));
        zoomInButton.setTooltip(new Tooltip("Zoom in"));

        previousButton.setOnAction(
                event -> showPage(Math.max(0, currentPageIndex - pagesPerSpread())));
        nextButton.setOnAction(event -> showPage(currentPageIndex + pagesPerSpread()));
        zoomOutButton.setOnAction(event -> changeZoom(-ZOOM_STEP));
        zoomInButton.setOnAction(event -> changeZoom(ZOOM_STEP));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox toolbar =
                new HBox(
                        8,
                        previousButton,
                        nextButton,
                        pageLabel,
                        spacer,
                        zoomOutButton,
                        zoomLabel,
                        zoomInButton);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.getStyleClass().add("pdf-viewer-toolbar");

        pageSurface.setAlignment(Pos.TOP_CENTER);
        pageSurface.setHgap(PAGE_GAP);
        pageSurface.setVgap(PAGE_GAP);
        pageSurface.setPadding(new Insets(24));
        pageSurface.getStyleClass().add("pdf-page-surface");

        scrollPane.setPannable(true);
        scrollPane.setFitToHeight(false);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("pdf-viewer-scroll");
        scrollPane.viewportBoundsProperty()
                .addListener((observable, oldBounds, newBounds) -> refreshSpreadForWidth());
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        getChildren().addAll(toolbar, scrollPane, statusLabel);
        setFocusTraversable(true);
        setOnKeyPressed(
                event -> {
                    KeyCode code = event.getCode();
                    if (code == KeyCode.LEFT || code == KeyCode.PAGE_UP) {
                        showPage(Math.max(0, currentPageIndex - pagesPerSpread()));
                        event.consume();
                    } else if (code == KeyCode.RIGHT || code == KeyCode.PAGE_DOWN) {
                        showPage(currentPageIndex + pagesPerSpread());
                        event.consume();
                    } else if (code == KeyCode.ADD || code == KeyCode.PLUS) {
                        changeZoom(ZOOM_STEP);
                        event.consume();
                    } else if (code == KeyCode.SUBTRACT || code == KeyCode.MINUS) {
                        changeZoom(-ZOOM_STEP);
                        event.consume();
                    }
                });
        setDocument(documentPath);
    }

    public void setDocument(Path documentPath) {
        this.documentPath = documentPath;
        currentPageIndex = 0;
        pageCount = 0;
        requestedSpreadSize = 1;
        renderedPageWidth = DEFAULT_RENDERED_PAGE_WIDTH;
        displayedPageImages.clear();
        pageSurface.getChildren().clear();
        showPage(0);
    }

    public void dispose() {
        renderRequest++;
        if (renderTask != null) {
            renderTask.cancel();
        }
        displayedPageImages.clear();
    }

    static int calculatePageColumns(
            double viewportWidth, double renderedPageWidth, double zoom) {
        if (zoom >= SINGLE_PAGE_ZOOM
                || viewportWidth <= 0
                || renderedPageWidth <= 0
                || zoom <= 0) {
            return 1;
        }

        double availableWidth = Math.max(0, viewportWidth - SURFACE_HORIZONTAL_INSETS);
        double scaledPageWidth = renderedPageWidth * zoom;
        int columns =
                (int)
                        Math.floor(
                                (availableWidth + PAGE_GAP) / (scaledPageWidth + PAGE_GAP));
        return Math.max(1, Math.min(MAX_PAGES_PER_SPREAD, columns));
    }

    private int pagesPerSpread() {
        double viewportWidth = scrollPane.getViewportBounds().getWidth();
        return calculatePageColumns(viewportWidth, renderedPageWidth, zoom);
    }

    private int expectedDisplayedPageCount() {
        if (pageCount < 1) {
            return pagesPerSpread();
        }
        return Math.min(pagesPerSpread(), pageCount - currentPageIndex);
    }

    private void refreshSpreadForWidth() {
        if (pageCount < 1) {
            return;
        }

        int expectedCount = expectedDisplayedPageCount();
        boolean matchingRenderInProgress =
                renderTask != null && renderTask.isRunning() && requestedSpreadSize == expectedCount;
        if (!matchingRenderInProgress && displayedPageImages.size() != expectedCount) {
            showPage(currentPageIndex);
        }
    }

    private void showPage(int requestedPageIndex) {
        if (documentPath == null) {
            showFailure("PDF file is unavailable.");
            return;
        }
        if (pageCount > 0 && (requestedPageIndex < 0 || requestedPageIndex >= pageCount)) {
            return;
        }

        int spreadSize = pagesPerSpread();
        requestedSpreadSize =
                pageCount > 0
                        ? Math.min(spreadSize, pageCount - requestedPageIndex)
                        : spreadSize;
        long request = ++renderRequest;
        if (renderTask != null) {
            renderTask.cancel();
        }

        int firstDisplayPage = requestedPageIndex + 1;
        int lastDisplayPage = firstDisplayPage + requestedSpreadSize - 1;
        statusLabel.setText(
                requestedSpreadSize == 1
                        ? "Loading page " + firstDisplayPage + "..."
                        : "Loading pages " + firstDisplayPage + "-" + lastDisplayPage + "...");
        setNavigationDisabled(true);

        renderTask = new Task<>() {
            @Override
            protected RenderedSpread call() throws IOException {
                try (PDDocument document = PDDocument.load(documentPath.toFile())) {
                    int totalPages = document.getNumberOfPages();
                    if (totalPages < 1) {
                        throw new IOException("This PDF has no pages.");
                    }

                    int safeFirstPage =
                            Math.max(0, Math.min(requestedPageIndex, totalPages - 1));
                    int pagesToRender = Math.min(requestedSpreadSize, totalPages - safeFirstPage);
                    PDFRenderer renderer = new PDFRenderer(document);
                    List<BufferedImage> images = new ArrayList<>(pagesToRender);
                    for (int offset = 0; offset < pagesToRender; offset++) {
                        if (isCancelled()) {
                            break;
                        }
                        images.add(
                                renderer.renderImageWithDPI(
                                        safeFirstPage + offset, RENDER_DPI, ImageType.RGB));
                    }
                    return new RenderedSpread(safeFirstPage, totalPages, List.copyOf(images));
                }
            }
        };

        renderTask.setOnSucceeded(
                event -> {
                    if (request != renderRequest) {
                        return;
                    }
                    RenderedSpread rendered = renderTask.getValue();
                    if (rendered.images().isEmpty()) {
                        return;
                    }

                    currentPageIndex = rendered.firstPageIndex();
                    pageCount = rendered.pageCount();
                    renderedPageWidth =
                            rendered.images().stream()
                                    .mapToInt(BufferedImage::getWidth)
                                    .max()
                                    .orElse((int) DEFAULT_RENDERED_PAGE_WIDTH);

                    int expectedCount = expectedDisplayedPageCount();
                    if (expectedCount != rendered.images().size()) {
                        showPage(currentPageIndex);
                        return;
                    }

                    displayRenderedSpread(rendered);
                    statusLabel.setText("");
                    scrollPane.setHvalue(.5);
                    scrollPane.setVvalue(0);
                    updateControls();
                });
        renderTask.setOnFailed(
                event -> {
                    if (request != renderRequest) {
                        return;
                    }
                    Throwable error = renderTask.getException();
                    showFailure(
                            "Could not display this PDF: "
                                    + (error == null ? "Unknown error" : error.getMessage()));
                });

        Thread worker = new Thread(renderTask, "pdf-page-spread-render-worker");
        worker.setDaemon(true);
        worker.start();
    }

    private void displayRenderedSpread(RenderedSpread rendered) {
        displayedPageImages.clear();
        List<VBox> pageCards = new ArrayList<>();

        for (int offset = 0; offset < rendered.images().size(); offset++) {
            int pageNumber = rendered.firstPageIndex() + offset + 1;
            ImageView pageImage = new ImageView(toFxImage(rendered.images().get(offset)));
            pageImage.setPreserveRatio(true);
            pageImage.setSmooth(true);
            pageImage.setAccessibleText("Rendered PDF page " + pageNumber);
            displayedPageImages.add(pageImage);

            StackPane sheet = new StackPane(pageImage);
            sheet.getStyleClass().add("pdf-page-sheet");

            Label pageNumberLabel = new Label(Integer.toString(pageNumber));
            pageNumberLabel.getStyleClass().add("pdf-page-number");

            VBox pageCard = new VBox(8, sheet, pageNumberLabel);
            pageCard.setAlignment(Pos.TOP_CENTER);
            pageCard.getStyleClass().add("pdf-page-card");
            pageCards.add(pageCard);
        }

        pageSurface.getChildren().setAll(pageCards);
        applyZoom();
        updatePageLabel();
    }

    private void changeZoom(double amount) {
        zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom + amount));
        applyZoom();

        int expectedCount = expectedDisplayedPageCount();
        boolean matchingRenderInProgress =
                renderTask != null && renderTask.isRunning() && requestedSpreadSize == expectedCount;
        if (pageCount > 0
                && !matchingRenderInProgress
                && displayedPageImages.size() != expectedCount) {
            showPage(currentPageIndex);
        } else {
            updatePageLabel();
            updateControls();
        }
    }

    private void applyZoom() {
        for (ImageView pageImage : displayedPageImages) {
            Image image = pageImage.getImage();
            if (image != null) {
                pageImage.setFitWidth(image.getWidth() * zoom);
            }
        }
        zoomLabel.setText(Math.round(zoom * 100) + "%");
    }

    private void updatePageLabel() {
        if (pageCount < 1 || displayedPageImages.isEmpty()) {
            pageLabel.setText("Page -");
            return;
        }

        int firstPage = currentPageIndex + 1;
        int lastPage = currentPageIndex + displayedPageImages.size();
        pageLabel.setText(
                firstPage == lastPage
                        ? "Page " + firstPage + " of " + pageCount
                        : "Pages " + firstPage + "-" + lastPage + " of " + pageCount);
    }

    private void updateControls() {
        previousButton.setDisable(currentPageIndex <= 0);
        nextButton.setDisable(
                pageCount < 1 || currentPageIndex + displayedPageImages.size() >= pageCount);
        zoomOutButton.setDisable(zoom <= MIN_ZOOM);
        zoomInButton.setDisable(zoom >= MAX_ZOOM);
    }

    private void setNavigationDisabled(boolean disabled) {
        previousButton.setDisable(disabled);
        nextButton.setDisable(disabled);
    }

    private void showFailure(String message) {
        displayedPageImages.clear();
        pageSurface.getChildren().clear();
        pageLabel.setText("Page -");
        statusLabel.setText(message);
        previousButton.setDisable(true);
        nextButton.setDisable(true);
    }

    private Image toFxImage(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        int[] pixels = source.getRGB(0, 0, width, height, null, 0, width);
        WritableImage image = new WritableImage(width, height);
        image.getPixelWriter()
                .setPixels(
                        0,
                        0,
                        width,
                        height,
                        PixelFormat.getIntArgbInstance(),
                        pixels,
                        0,
                        width);
        return image;
    }

    private record RenderedSpread(
            int firstPageIndex, int pageCount, List<BufferedImage> images) {}
}
