package com.gallery.service;

import com.gallery.dao.UserDao;
import com.gallery.model.User;
import com.gallery.util.ValidationException;
import com.gallery.util.ValidationUtil;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Locale;
import java.util.Optional;
import org.mindrot.jbcrypt.BCrypt;

/** Registration and credential checking. Passwords are bcrypt-hashed. */
public class AuthService {

    private static final int BCRYPT_COST = 12;

    private final UserDao userDao = new UserDao();

    /**
     * Registers a new user.
     *
     * @throws ValidationException when the username or password is invalid or taken
     */
    public User register(String rawUsername, String password, String confirmPassword) throws SQLException {
        String username = ValidationUtil.requireUsername(rawUsername);
        ValidationUtil.requirePassword(password, confirmPassword);
        if (userDao.existsByUsername(username)) {
            throw new ValidationException("That username is already taken.");
        }
        String hash = BCrypt.hashpw(password, BCrypt.gensalt(BCRYPT_COST));
        try {
            long userId = userDao.create(username, hash);
            User user = new User();
            user.setUserId(userId);
            user.setUsername(username);
            return user;
        } catch (SQLIntegrityConstraintViolationException e) {
            // Another request created the same username between the check and insert.
            throw new ValidationException("That username is already taken.");
        }
    }

    /**
     * Verifies credentials. Returns an empty Optional for both unknown users
     * and wrong passwords so callers cannot distinguish the two.
     */
    public Optional<User> login(String rawUsername, String password) throws SQLException {
        if (rawUsername == null || password == null) {
            return Optional.empty();
        }
        String username = rawUsername.trim();
        if (username.isEmpty() || password.isEmpty()) {
            return Optional.empty();
        }
        Optional<User> found = userDao.findByUsername(username);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        User user = found.get();
        try {
            if (!BCrypt.checkpw(password, user.getPasswordHash())) {
                return Optional.empty();
            }
        } catch (IllegalArgumentException e) {
            // Malformed stored hash: treat as a failed login, never log the hash.
            return Optional.empty();
        }
        user.setPasswordHash(null);
        return Optional.of(user);
    }

    /** Normalises a username for display/lookup without validating the format. */
    public static String displayName(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }
}
