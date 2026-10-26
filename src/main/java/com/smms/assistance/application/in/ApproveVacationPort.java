package com.smms.assistance.application.in;

import com.smms.assistance.domain.entity.VacationRequestEntity;
import java.util.UUID;

public interface ApproveVacationPort {
    VacationRequestEntity execute(UUID requestId, UUID reviewerId, Integer discountDays);
}
