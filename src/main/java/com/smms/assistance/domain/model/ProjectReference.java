package com.smms.assistance.domain.model;

import java.util.UUID;

/**
 * Lightweight reference to a Project entity managed by another service.
 * This service does NOT own the projects table.
 */
public record ProjectReference(UUID projectId, String name) {}
