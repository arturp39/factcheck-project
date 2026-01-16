package com.factcheck.backend.service;

import com.factcheck.backend.entity.AppUser;
import com.factcheck.backend.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public AppUser registerUser(String username, String password) {
        String normalized = normalize(username);
        validateCredentials(normalized, password);
        if (userRepository.existsByUsername(normalized)) {
            throw new IllegalArgumentException("Username already exists.");
        }

        AppUser user = new AppUser();
        user.setUsername(normalized);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole("USER");
        return userRepository.save(user);
    }

    public Optional<AppUser> findByUsername(String username) {
        return userRepository.findByUsername(normalize(username));
    }

    public AppUser ensureAdminUser(String username, String password) {
        String normalized = normalize(username);
        if (normalized.isBlank() || password == null || password.isBlank()) {
            throw new IllegalArgumentException("Admin credentials must be provided.");
        }
        return userRepository.findByUsername(normalized)
                .orElseGet(() -> {
                    AppUser admin = new AppUser();
                    admin.setUsername(normalized);
                    admin.setPasswordHash(passwordEncoder.encode(password));
                    admin.setRole("ADMIN");
                    return userRepository.save(admin);
                });
    }

    private void validateCredentials(String username, String password) {
        if (username.isBlank()) {
            throw new IllegalArgumentException("Username must not be empty.");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password must not be empty.");
        }
        if (password.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters.");
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
