package com.smms.assistance.application.usecase;

import com.github.f4b6a3.uuid.UuidCreator;
import com.smms.assistance.application.in.NotificationPort;
import com.smms.assistance.domain.entity.Notification;
import com.smms.assistance.shared.util.TimezoneService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.UUID;

@ApplicationScoped
public class CreateNotificationUseCase {

    private final NotificationPort notificationPort;

    @Inject
    public CreateNotificationUseCase(NotificationPort notificationPort) {
        this.notificationPort = notificationPort;
    }

    public Notification execute(UUID userId,
                                String type,
                                String severity,
                                String title,
                                String message,
                                String referenceType,
                                UUID referenceId) {

        if (userId == null) {
            throw new IllegalArgumentException("userId cannot be null");
        }

        Notification notification = new Notification(
                UuidCreator.getTimeOrderedEpoch(), // UUIDv7
                userId,
                type,
                severity,
                title,
                message,
                referenceType,
                referenceId,
                TimezoneService.utcNow()
        );

        return notificationPort.create(notification);
    }
}