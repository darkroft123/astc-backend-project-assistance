package com.smms.assistance.application.in;

import com.smms.assistance.domain.entity.Notification;
import java.util.List;
import java.util.UUID;

public interface NotificationPort {

    Notification create(Notification notification);

    List<Notification> findAlertsByUserId(UUID userId);

    List<Notification> findAllAlerts();

    boolean delete(UUID id);

    List<Notification> findByUserIdAndType(
        UUID userId,
        String type
    );

}