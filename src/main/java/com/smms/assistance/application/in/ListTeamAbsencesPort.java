package com.smms.assistance.application.in;

import com.smms.assistance.domain.entity.AbsenceEntity;
import com.smms.assistance.domain.model.Page;

import java.time.LocalDate;
import java.util.UUID;

public interface ListTeamAbsencesPort {
    Page<AbsenceEntity> execute(int page, int size, UUID projectId, UUID userId, LocalDate fromDate, LocalDate toDate, String type, Boolean justified);
}
