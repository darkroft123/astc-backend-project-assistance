package com.smms.assistance.application.usecase;

import com.smms.assistance.application.in.ListJustificationsPMPort;
import com.smms.assistance.domain.entity.JustificationEntity;
import com.smms.assistance.application.service.TeamMembershipResolver;
import com.smms.assistance.infrastructure.client.AssistanceClientWrapper;
import com.smms.assistance.infrastructure.security.AuthContext;
import com.smms.assistance.domain.model.Page;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class ListJustificationsPMUseCase implements ListJustificationsPMPort {

    @Inject
    AssistanceClientWrapper clientWrapper;

    @Inject
    AuthContext authContext;

    @Inject
    TeamMembershipResolver teamMembershipResolver;

    @Override
    public Page<JustificationEntity> execute(int page, int size, UUID userId, String status, LocalDate fromDate, LocalDate toDate) {
        UUID currentPmId = authContext.getUserId();
        
        System.out.println("[PM Justifications] Project Assistance -> Assistance client call");
        System.out.println("  Query name: ListTeamJustificationsPM");

        // 1. Resolve project members managed by this PM
        List<UUID> userIds = teamMembershipResolver.resolveManagedByPm(userId, currentPmId, true);

        System.out.println("  Number of project members resolved: " + userIds.size());

        System.out.println("[PM Justifications] Justification history loading");
        System.out.println("  Resolved member IDs: " + userIds);
        System.out.println("  Pagination parameters: page=" + page + ", size=" + size);

        if (userIds.isEmpty()) {
            System.out.println("[PM Justifications] Records returned: 0 (No members resolved)");
            return Page.<JustificationEntity>builder()
                    .items(List.of())
                    .page(page)
                    .size(size)
                    .totalItems(0L)
                    .build();
        }

        // 2. Fetch from Assistance Service
        String fromStr = fromDate != null ? fromDate.toString() : null;
        String toStr = toDate != null ? toDate.toString() : null;

        var clientResponse = clientWrapper.getClient().listTeamJustificationsPM(
                userIds, status, fromStr, toStr, page, size
        );

        int recordsReturned = (clientResponse != null && clientResponse.getItems() != null) ? clientResponse.getItems().size() : 0;
        long totalItems = clientResponse != null ? clientResponse.getTotal() : 0L;

        System.out.println("  Number of justifications returned: " + recordsReturned);
        System.out.println("[PM Justifications] Records returned: " + recordsReturned + " (Total: " + totalItems + ")");

        // 3. Map to local JustificationEntity
        List<JustificationEntity> items = List.of();
        if (clientResponse != null && clientResponse.getItems() != null) {
            items = clientResponse.getItems().stream()
                    .map(dto -> {
                        JustificationEntity e = new JustificationEntity();
                        e.setId(dto.getId());
                        e.setAbsenceId(dto.getAbsenceId());
                        e.setUserId(dto.getUserId());
                        e.setDescription(dto.getDescription());
                        e.setDocumentUrl(dto.getDocumentUrl());
                        e.setStatus(dto.getStatus());
                        e.setComment(dto.getComment());
                        e.setSubmittedAt(dto.getSubmittedAt() != null ? LocalDateTime.parse(dto.getSubmittedAt()) : null);
                        e.setAbsenceDate(dto.getAbsenceDate());
                        e.setAbsenceType(dto.getAbsenceType());
                        return e;
                    })
                    .collect(Collectors.toList());
        }

        return Page.<JustificationEntity>builder()
                .items(items)
                .page(page)
                .size(size)
                .totalItems(totalItems)
                .build();
    }
}
