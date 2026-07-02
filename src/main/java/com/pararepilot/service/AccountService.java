package com.pararepilot.service;

import java.sql.SQLException;
import java.util.Optional;

import com.pararepilot.model.User;
import com.pararepilot.repository.RememberedSessionRepository;
import com.pararepilot.repository.UserRepository;

public class AccountService {

    private final UserRepository userRepository;
    private final RememberedSessionRepository rememberedSessionRepository;
    private final PasswordHasher passwordHasher;

    public AccountService() {
        this(new UserRepository(), new RememberedSessionRepository(), new PasswordHasher());
    }

    public AccountService(UserRepository userRepository, PasswordHasher passwordHasher) {
        this(userRepository, new RememberedSessionRepository(), passwordHasher);
    }

    public AccountService(
            UserRepository userRepository,
            RememberedSessionRepository rememberedSessionRepository,
            PasswordHasher passwordHasher
    ) {
        this.userRepository = userRepository;
        this.rememberedSessionRepository = rememberedSessionRepository;
        this.passwordHasher = passwordHasher;
    }

    public User createAccount(String username, char[] password, char[] confirmPassword)
            throws SQLException {

        String normalizedUsername = validateUsername(username);
        validatePassword(password, confirmPassword);

        if (userRepository.findByUsername(normalizedUsername).isPresent()) {
            throw new IllegalArgumentException("That username is already taken.");
        }

        String salt = passwordHasher.newSalt();
        String hash = passwordHasher.hash(password, salt);
        User user = userRepository.create(normalizedUsername, hash, salt);
        userRepository.claimLegacyData(user.id());
        AccountSession.signIn(user);
        rememberedSessionRepository.remember(user);
        return user;
    }

    public User login(String username, char[] password) throws SQLException {
        String normalizedUsername = validateUsername(username);

        if (password == null || password.length == 0) {
            throw new IllegalArgumentException("Enter your password.");
        }

        User user = userRepository.findByUsername(normalizedUsername)
                .orElseThrow(() -> new IllegalArgumentException("Username or password is incorrect."));

        if (!passwordHasher.matches(password, user.passwordSalt(), user.passwordHash())) {
            throw new IllegalArgumentException("Username or password is incorrect.");
        }

        userRepository.ensureStatsRow(user.id());
        AccountSession.signIn(user);
        rememberedSessionRepository.remember(user);
        return user;
    }

    public void changePassword(char[] currentPassword, char[] newPassword, char[] confirmPassword)
            throws SQLException {

        User user = AccountSession.currentUser()
                .orElseThrow(() -> new IllegalStateException("No account is signed in."));

        if (!passwordHasher.matches(currentPassword, user.passwordSalt(), user.passwordHash())) {
            throw new IllegalArgumentException("Current password is incorrect.");
        }

        validatePassword(newPassword, confirmPassword);

        String salt = passwordHasher.newSalt();
        String hash = passwordHasher.hash(newPassword, salt);
        userRepository.updatePassword(user.id(), hash, salt);
        User refreshedUser = userRepository.findById(user.id())
                .orElseThrow(() -> new SQLException("Updated user could not be reloaded."));
        AccountSession.signIn(refreshedUser);
        rememberedSessionRepository.remember(refreshedUser);
    }

    public Optional<User> restoreRememberedAccount() throws SQLException {
        Optional<User> rememberedUser = rememberedSessionRepository.findRememberedUser();
        rememberedUser.ifPresent(AccountSession::signIn);
        return rememberedUser;
    }

    public void signOut() throws SQLException {
        rememberedSessionRepository.clear();
        AccountSession.signOut();
    }

    public boolean hasAccounts() throws SQLException {
        return userRepository.countVisibleUsers() > 0;
    }

    private String validateUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Enter a username.");
        }

        String normalizedUsername = username.trim();

        if (normalizedUsername.length() < 3) {
            throw new IllegalArgumentException("Username must be at least 3 characters.");
        }

        if (normalizedUsername.equalsIgnoreCase("__local__")) {
            throw new IllegalArgumentException("Choose a different username.");
        }

        return normalizedUsername;
    }

    private void validatePassword(char[] password, char[] confirmPassword) {
        if (password == null || password.length < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters.");
        }

        if (confirmPassword == null || password.length != confirmPassword.length) {
            throw new IllegalArgumentException("Passwords do not match.");
        }

        for (int i = 0; i < password.length; i++) {
            if (password[i] != confirmPassword[i]) {
                throw new IllegalArgumentException("Passwords do not match.");
            }
        }
    }
}
