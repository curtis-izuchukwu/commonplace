package com.commonplace.model;

import java.time.LocalDateTime;

public record User(
        long id,
        String username,
        String passwordHash,
        String passwordSalt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
