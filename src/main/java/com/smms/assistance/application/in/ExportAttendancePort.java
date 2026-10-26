package com.smms.assistance.application.in;

import com.smms.assistance.infrastructure.controller.graphql.dto.ExportResultOutput;

import java.time.LocalDate;
import java.util.UUID;

public interface ExportAttendancePort {
    ExportResultOutput execute(UUID projectId, UUID userId, LocalDate fromDate, LocalDate toDate, String status);
}
