package com.smms.assistance.infrastructure.controller.graphql.dto;

import com.smms.assistance.infrastructure.controller.graphql.dto.UserOutput;

import java.time.LocalDateTime;
import java.util.UUID;

public class NotificationOutput {

    public UUID id;
    public UUID userId;

    public UserOutput user; // 🔥 usuario desde auth-service

    public String type;
    public String severity;
    public String title;
    public String message;
    public String referenceType;
    public UUID referenceId;
    public LocalDateTime createdAt;

    public NotificationOutput() {
    }

    public NotificationOutput(
            UUID id,
            UUID userId,
            String type,
            String severity,
            String title,
            String message,
            String referenceType,
            UUID referenceId,
            LocalDateTime createdAt
    ) {
        this.id = id;
        this.userId = userId;
        this.type = type;
        this.severity = severity;
        this.title = title;
        this.message = message;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.createdAt = createdAt;
    }
}