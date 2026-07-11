package com.pararepilot.importer;

public record ImportIssue(
        ImportIssueSeverity severity,
        String message
) {
}
