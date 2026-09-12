package com.commonplace.importer;

public record ImportIssue(
        ImportIssueSeverity severity,
        String message
) {
}
