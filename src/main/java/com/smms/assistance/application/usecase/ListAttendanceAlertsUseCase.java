package com.smms.assistance.application.usecase;

import com.smms.assistance.application.in.ListAttendanceAlertsPort;
import com.smms.assistance.domain.entity.AlertEntity;
import com.smms.assistance.infrastructure.repository.AlertRepository;
import com.smms.assistance.domain.model.Page;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class ListAttendanceAlertsUseCase implements ListAttendanceAlertsPort {

    @Inject
    AlertRepository alertRepository;

    @Override
    public Page<AlertEntity> execute(int page, int size, String status, String type, LocalDate from, LocalDate to, UUID scopedToUserId) {
        return alertRepository.findPageByCriteria(status, type, from, to, page, size, scopedToUserId);
    }
}
