package com.smms.assistance.application.in;

import com.smms.assistance.domain.entity.VacationRequestEntity;
import java.util.UUID;

public interface RejectVacationPort {
    VacationRequestEntity execute(UUID requestId, UUID reviewerId, String comment);
}
