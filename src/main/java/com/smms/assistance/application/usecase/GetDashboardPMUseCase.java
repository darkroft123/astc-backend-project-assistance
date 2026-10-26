package com.smms.assistance.application.usecase;

import com.smms.assistance.application.in.GetDashboardPMPort;
import com.smms.assistance.domain.model.DashboardSummary;
import com.smms.assistance.application.service.TeamMembershipResolver;
import com.smms.assistance.infrastructure.client.AssistanceClientWrapper;
import com.smms.assistance.infrastructure.security.AuthContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class GetDashboardPMUseCase implements GetDashboardPMPort {

    @Inject
    AssistanceClientWrapper clientWrapper;

    @Inject
    AuthContext authContext;

    @Inject
    TeamMembershipResolver teamMembershipResolver;

    @Override
    public DashboardSummary execute(UUID projectId, UUID userId, LocalDate fromDate, LocalDate toDate) {
        UUID currentPmId = authContext.getUserId();
        
        // 1. Resolve project members
        List<UUID> userIds = teamMembershipResolver.resolve(projectId, userId, currentPmId);

        System.out.println("[PM Attendance] Project Assistance -> Assistance client call");
        System.out.println("  Query name: GetDashboardPMInternal");
        System.out.println("  Project ID: " + projectId);
        System.out.println("  Number of project members resolved: " + userIds.size());

        if (userIds.isEmpty()) {
            return DashboardSummary.builder()
                    .totalAttendances(0)
                    .totalAbsences(0)
                    .pendingJustifications(0)
                    .build();
        }

        String fromStr = fromDate != null ? fromDate.toString() : null;
        String toStr = toDate != null ? toDate.toString() : null;

        var clientResponse = clientWrapper.getClient().getDashboardPMInternal(
                userIds, projectId, fromStr, toStr
        );

        int totalAttendances = clientResponse != null && clientResponse.getTotalAttendances() != null ? clientResponse.getTotalAttendances() : 0;
        int totalAbsences = clientResponse != null && clientResponse.getTotalAbsences() != null ? clientResponse.getTotalAbsences() : 0;
        int pendingJustifications = clientResponse != null && clientResponse.getPendingJustifications() != null ? clientResponse.getPendingJustifications() : 0;

        System.out.println("  Number of attendance records returned: " + totalAttendances);
        System.out.println("  Number of justifications returned: " + pendingJustifications);

        return DashboardSummary.builder()
                .totalAttendances(totalAttendances)
                .totalAbsences(totalAbsences)
                .pendingJustifications(pendingJustifications)
                .build();
    }
}

