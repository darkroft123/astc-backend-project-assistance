package com.smms.assistance.application.in;

import com.smms.assistance.domain.entity.AlertEntity;
import java.util.UUID;

public interface GetAlertDetailPort {
    AlertEntity execute(UUID alertId);
}
