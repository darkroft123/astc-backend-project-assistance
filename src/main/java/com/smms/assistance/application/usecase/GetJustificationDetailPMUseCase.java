package com.smms.assistance.application.usecase;

import com.smms.assistance.application.in.GetJustificationDetailPMPort;
import com.smms.assistance.domain.entity.JustificationEntity;
import com.smms.assistance.infrastructure.client.AssistanceClientWrapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class GetJustificationDetailPMUseCase implements GetJustificationDetailPMPort {

    @Inject
    AssistanceClientWrapper clientWrapper;

    @Override
    public JustificationEntity execute(UUID justificationId) {
        System.out.println("[PM Justifications] Project Assistance -> Assistance client call");
        System.out.println("  Query name: GetJustificationDetailPM");
        System.out.println("  Justification ID: " + justificationId);

        var dto = clientWrapper.getClient().getJustificationDetailPM(justificationId);
        if (dto == null) {
            throw new IllegalArgumentException("Justification not found");
        }

        JustificationEntity e = new JustificationEntity();
        e.setId(dto.getId());
        e.setAbsenceId(dto.getAbsenceId());
        e.setUserId(dto.getUserId());
        e.setDescription(dto.getDescription());
        e.setDocumentUrl(dto.getDocumentUrl());
        e.setStatus(dto.getStatus());
        e.setComment(dto.getComment());
        e.setSubmittedAt(dto.getSubmittedAt() != null ? LocalDateTime.parse(dto.getSubmittedAt()) : null);
        e.setReviewedAt(dto.getReviewedAt() != null ? LocalDateTime.parse(dto.getReviewedAt()) : null);
        e.setAbsenceDate(dto.getAbsenceDate());
        e.setAbsenceType(dto.getAbsenceType());
        if (dto.getHistory() != null) {
            e.setHistory(dto.getHistory().stream()
                    .map(h -> com.smms.assistance.infrastructure.controller.graphql.dto.JustificationHistoryOutput.builder()
                            .id(h.getId())
                            .justificationId(h.getJustificationId())
                            .previousStatus(h.getPreviousStatus())
                            .newStatus(h.getNewStatus())
                            .comment(h.getComment())
                            .changedBy(h.getChangedBy())
                            .changedAt(h.getChangedAt())
                            .build())
                    .collect(Collectors.toList()));
        }
        return e;
    }
}

