package com.commonplace.service;

import java.sql.SQLException;
import java.util.Optional;

import com.commonplace.model.User;
import com.commonplace.repository.UserRepository;

public final class AccountSession {

    private static User currentUser;

    private AccountSession() {
        // Utility class
    }

    public static Optional<User> currentUser() {
        return Optional.ofNullable(currentUser);
    }

    public static long currentUserId() throws SQLException {
        if (currentUser == null) {
            currentUser = new UserRepository().ensureLocalFallbackUser();
        }

        return currentUser.id();
    }

    public static void signIn(User user) {
        currentUser = user;
    }

    public static void signOut() {
        currentUser = null;
    }
}
