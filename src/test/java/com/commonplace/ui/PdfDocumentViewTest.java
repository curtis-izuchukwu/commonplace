package com.commonplace.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PdfDocumentViewTest {

    @Test
    void normalZoomAlwaysKeepsOneCenteredPage() {
        assertEquals(1, PdfDocumentView.calculatePageColumns(1920, 992, .8));
        assertEquals(1, PdfDocumentView.calculatePageColumns(3840, 992, .75));
    }

    @Test
    void progressivelyAddsPagesWhenZoomingOut() {
        assertEquals(2, PdfDocumentView.calculatePageColumns(1920, 992, .65));
        assertEquals(3, PdfDocumentView.calculatePageColumns(1920, 992, .5));
        assertEquals(5, PdfDocumentView.calculatePageColumns(1920, 992, .35));
    }

    @Test
    void pageCountStillRespectsAvailableWidthAndSafetyCap() {
        assertEquals(1, PdfDocumentView.calculatePageColumns(900, 992, .5));
        assertEquals(6, PdfDocumentView.calculatePageColumns(10000, 992, .3));
    }
}
