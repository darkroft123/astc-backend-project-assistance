package com.smms.assistance.application.usecase;

import com.smms.assistance.application.in.NotificationPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.UUID;

@ApplicationScoped
public class DeleteNotificationUseCase {

    private final NotificationPort notificationPort;

    @Inject
    public DeleteNotificationUseCase(NotificationPort notificationPort) {
        this.notificationPort = notificationPort;
    }

    public boolean execute(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("Notification id cannot be null");
        }
        return notificationPort.delete(id);
    }
}