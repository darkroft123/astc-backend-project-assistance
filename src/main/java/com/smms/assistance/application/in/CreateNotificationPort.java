package com.smms.assistance.application.in;

import com.smms.assistance.domain.entity.Notification;
import java.util.UUID;

public interface CreateNotificationPort {

    Notification execute(
            UUID userId,
            String type,
            String severity,
            String title,
            String message,
            String referenceType,
            UUID referenceId
    );
}