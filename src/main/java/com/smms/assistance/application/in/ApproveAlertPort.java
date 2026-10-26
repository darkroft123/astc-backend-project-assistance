package com.smms.assistance.application.in;

import com.smms.assistance.domain.entity.AlertEntity;
import java.util.UUID;

public interface ApproveAlertPort {
    AlertEntity execute(UUID alertId, UUID reviewedBy);
}
