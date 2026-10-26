package com.smms.assistance.application.in;

import com.smms.assistance.domain.entity.JustificationEntity;
import java.util.UUID;

public interface GetJustificationDetailPMPort {
    JustificationEntity execute(UUID justificationId);
}
