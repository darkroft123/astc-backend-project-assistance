package com.smms.assistance.application.in;

import com.smms.assistance.domain.model.DashboardSummary;
import java.time.LocalDate;
import java.util.UUID;

public interface GetDashboardPMPort {
    DashboardSummary execute(UUID projectId, UUID userId, LocalDate fromDate, LocalDate toDate);
}
