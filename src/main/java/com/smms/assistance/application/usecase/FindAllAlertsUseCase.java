package com.smms.assistance.application.usecase;

import com.smms.assistance.application.in.NotificationPort;
import com.smms.assistance.domain.entity.Notification;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
public class FindAllAlertsUseCase {

    private final NotificationPort notificationPort;

    @Inject
    public FindAllAlertsUseCase(NotificationPort notificationPort) {
        this.notificationPort = notificationPort;
    }

    public List<Notification> execute() {
        return notificationPort.findAllAlerts();
    }
}