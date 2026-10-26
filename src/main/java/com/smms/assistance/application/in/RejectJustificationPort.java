package com.smms.assistance.application.in;

import com.smms.assistance.domain.entity.JustificationEntity;

import java.util.UUID;

public interface RejectJustificationPort {
    JustificationEntity execute(UUID justificationId, String comment, UUID reviewedBy);
}
