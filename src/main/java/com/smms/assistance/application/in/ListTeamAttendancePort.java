package com.smms.assistance.application.in;

import com.smms.assistance.domain.entity.AttendanceEntity;
import com.smms.assistance.domain.model.Page;

import java.time.LocalDate;
import java.util.UUID;

public interface ListTeamAttendancePort {
    Page<AttendanceEntity> execute(int page, int size, UUID projectId, UUID userId, LocalDate fromDate, LocalDate toDate, String status);
}
