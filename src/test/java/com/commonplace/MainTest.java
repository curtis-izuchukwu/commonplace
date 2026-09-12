package com.commonplace;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class MainTest {
    @Test
    void stylesheetResourceExists() {
        assertNotNull(Main.class.getResource("/com/commonplace/css/app.css"));
    }
}
