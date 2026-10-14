package com.commonplace.service;

import java.sql.SQLException;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import com.commonplace.model.User;
import com.commonplace.repository.UserRepository;

public final class AccountSession {

    private static volatile User currentUser;
    private static final CopyOnWriteArrayList<Consumer<User>> BEFORE_CHANGE = new CopyOnWriteArrayList<>();

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
        BEFORE_CHANGE.forEach(listener -> listener.accept(user));
        currentUser = user;
    }

    public static void signOut() {
        BEFORE_CHANGE.forEach(listener -> listener.accept(null));
        currentUser = null;
    }

    /** Allows secondary workspaces to prevent an account transition that would lose edits. */
    public static void addBeforeChangeListener(Consumer<User> listener) {
        BEFORE_CHANGE.add(listener);
    }
}
