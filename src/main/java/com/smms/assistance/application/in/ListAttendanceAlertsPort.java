package com.smms.assistance.application.in;

import com.smms.assistance.domain.entity.AlertEntity;
import com.smms.assistance.domain.model.Page;

import java.time.LocalDate;
import java.util.UUID;

public interface ListAttendanceAlertsPort {
    Page<AlertEntity> execute(int page, int size, String status, String type, LocalDate from, LocalDate to, UUID scopedToUserId);
}
