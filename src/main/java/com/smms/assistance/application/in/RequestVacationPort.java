package com.smms.assistance.application.in;

import com.smms.assistance.domain.entity.VacationRequestEntity;
import java.time.LocalDate;
import java.util.UUID;

public interface RequestVacationPort {
    VacationRequestEntity execute(UUID userId, UUID projectId, LocalDate startDate, LocalDate endDate, Integer businessDays, String comment);
}
