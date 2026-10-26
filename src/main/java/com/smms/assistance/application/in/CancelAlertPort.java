package com.smms.assistance.application.in;

import com.smms.assistance.domain.entity.AlertEntity;
import java.util.UUID;

public interface CancelAlertPort {
    AlertEntity execute(UUID alertId, UUID reviewedBy);
}
