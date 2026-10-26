package com.smms.assistance.application.in;

import com.smms.assistance.domain.entity.JustificationEntity;
import com.smms.assistance.domain.model.Page;

import java.time.LocalDate;
import java.util.UUID;

public interface ListJustificationsPMPort {
    Page<JustificationEntity> execute(int page, int size, UUID userId, String status, LocalDate fromDate, LocalDate toDate);
}
