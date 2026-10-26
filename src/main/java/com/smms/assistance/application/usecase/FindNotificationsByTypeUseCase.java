package com.smms.assistance.application.usecase;

import com.smms.assistance.application.in.NotificationPort;
import com.smms.assistance.domain.entity.Notification;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class FindNotificationsByTypeUseCase {

    private final NotificationPort notificationPort;

    @Inject
    public FindNotificationsByTypeUseCase(
            NotificationPort notificationPort
    ) {
        this.notificationPort = notificationPort;
    }

    public List<Notification> execute(
            UUID userId,
            String type
    ) {

        if(userId == null){
            throw new IllegalArgumentException(
                    "userId cannot be null"
            );
        }

        if(type == null || type.isBlank()){
            throw new IllegalArgumentException(
                    "type cannot be null"
            );
        }

        return notificationPort.findByUserIdAndType(
                userId,
                type
        );
    }
}