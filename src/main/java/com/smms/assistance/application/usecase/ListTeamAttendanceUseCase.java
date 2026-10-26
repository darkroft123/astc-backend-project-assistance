package com.smms.assistance.application.usecase;

import com.smms.assistance.application.in.ListTeamAttendancePort;
import com.smms.assistance.domain.entity.AttendanceEntity;
import com.smms.assistance.application.service.TeamMembershipResolver;
import com.smms.assistance.infrastructure.client.AssistanceClientWrapper;
import com.smms.assistance.infrastructure.security.AuthContext;
import com.smms.assistance.domain.model.Page;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class ListTeamAttendanceUseCase implements ListTeamAttendancePort {

    @Inject
    AssistanceClientWrapper clientWrapper;

    @Inject
    AuthContext authContext;

    @Inject
    TeamMembershipResolver teamMembershipResolver;

    @Override
    public Page<AttendanceEntity> execute(int page, int size, UUID projectId, UUID userId, LocalDate fromDate, LocalDate toDate, String status) {
        UUID currentPmId = authContext.getUserId();
        
        System.out.println("[PM Attendance] Project Assistance -> Assistance client call");
        System.out.println("  Query name: ListTeamAttendancePM");
        System.out.println("  Project ID: " + projectId);

        // 1. Resolve project members
        List<UUID> userIds = teamMembershipResolver.resolve(projectId, userId, currentPmId);

        System.out.println("  Number of project members resolved: " + userIds.size());

        System.out.println("[PM Attendance] Attendance history loading");
        System.out.println("  Selected project: " + projectId);
        System.out.println("  Resolved member IDs: " + userIds);
        System.out.println("  Pagination parameters: page=" + page + ", size=" + size);

        if (userIds.isEmpty()) {
            System.out.println("[PM Attendance] Records returned: 0 (No members resolved)");
            return Page.<AttendanceEntity>builder()
                    .items(List.of())
                    .page(page)
                    .size(size)
                    .totalItems(0L)
                    .build();
        }

        // 2. Fetch from Assistance Service
        String fromStr = fromDate != null ? fromDate.toString() : null;
        String toStr = toDate != null ? toDate.toString() : null;

        com.smms.assistance.infrastructure.controller.graphql.dto.AttendancePageOutput clientResponse = null;
        try {
            System.out.println("[PM Attendance] Calling assistance-service");
            System.out.println("[PM Attendance] GraphQL query generated: Query: ListTeamAttendancePM, Variables: userIds=" + userIds + ", projectId=" + projectId + ", fromDate=" + fromStr + ", toDate=" + toStr + ", status=" + status + ", page=" + page + ", size=" + size);

            clientResponse = clientWrapper.getClient().listTeamAttendancePM(
                    userIds, projectId, fromStr, toStr, status, page, size
            );

            int recordsReturned = (clientResponse != null && clientResponse.getItems() != null) ? clientResponse.getItems().size() : 0;
            long totalItems = clientResponse != null ? clientResponse.getTotal() : 0L;
            System.out.println("[PM Attendance] Response received. Status: SUCCESS. Items: " + recordsReturned + ", Total: " + totalItems);
        } catch (Exception e) {
            System.out.println("[PM Attendance] Error details: " + e.getMessage());
            if (e instanceof io.smallrye.graphql.client.GraphQLClientException) {
                var gce = (io.smallrye.graphql.client.GraphQLClientException) e;
                if (gce.getErrors() != null) {
                    gce.getErrors().forEach(err -> System.out.println("  - GraphQL Client Error: " + err.getMessage()));
                }
            }
            e.printStackTrace();
            throw e;
        }

        int recordsReturned = (clientResponse != null && clientResponse.getItems() != null) ? clientResponse.getItems().size() : 0;
        long totalItems = clientResponse != null ? clientResponse.getTotal() : 0L;

        System.out.println("  Number of attendance records returned: " + recordsReturned);
        System.out.println("[PM Attendance] Records returned: " + recordsReturned + " (Total: " + totalItems + ")");

        // 3. Map to local AttendanceEntity
        List<AttendanceEntity> items = List.of();
        if (clientResponse != null && clientResponse.getItems() != null) {
            items = clientResponse.getItems().stream()
                    .map(dto -> AttendanceEntity.builder()
                            .id(dto.getId())
                            .userId(dto.getUserId())
                            .projectId(dto.getProjectId())
                            .date(dto.getDate() != null ? LocalDate.parse(dto.getDate()) : null)
                            .checkIn(dto.getCheckIn() != null ? LocalTime.parse(dto.getCheckIn()) : null)
                            .checkOut(dto.getCheckOut() != null ? LocalTime.parse(dto.getCheckOut()) : null)
                            .status(dto.getStatus())
                            .latitude(dto.getLatitude())
                            .longitude(dto.getLongitude())
                            .photoUrl(dto.getPhotoUrl())
                            .build())
                    .collect(Collectors.toList());
        }

        return Page.<AttendanceEntity>builder()
                .items(items)
                .page(page)
                .size(size)
                .totalItems(totalItems)
                .build();
    }
}
