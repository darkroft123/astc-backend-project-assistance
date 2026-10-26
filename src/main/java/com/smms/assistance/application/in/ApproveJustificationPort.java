package com.smms.assistance.application.in;

import com.smms.assistance.domain.entity.JustificationEntity;

import java.util.UUID;

public interface ApproveJustificationPort {
    JustificationEntity execute(UUID justificationId, String comment, UUID reviewedBy);
}
