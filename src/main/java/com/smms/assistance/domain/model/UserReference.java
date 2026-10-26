package com.smms.assistance.domain.model;

import java.util.UUID;

/**
 * Lightweight reference to a User entity managed by another service.
 * This service does NOT own the users table.
 */
public record UserReference(UUID userId, String email, String role) {}
