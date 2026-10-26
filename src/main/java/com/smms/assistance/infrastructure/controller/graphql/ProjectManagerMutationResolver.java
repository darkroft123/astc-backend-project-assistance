package com.smms.assistance.infrastructure.controller.graphql;

import com.smms.assistance.application.in.*;
import com.smms.assistance.application.usecase.*;

import com.smms.assistance.domain.entity.*;

import com.smms.assistance.infrastructure.client.AuthClient;
import com.smms.assistance.infrastructure.client.BackofficeClient;
import com.smms.assistance.infrastructure.controller.graphql.dto.*;
import com.smms.assistance.infrastructure.repository.HolidayRepository;
import com.smms.assistance.infrastructure.security.AuthContext;
import com.smms.assistance.application.service.UserService;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Mutation;
import org.eclipse.microprofile.graphql.Name;
import org.eclipse.microprofile.jwt.JsonWebToken;
import io.smallrye.graphql.client.typesafe.api.TypesafeGraphQLClientBuilder;
import org.jboss.logging.Logger;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import jakarta.transaction.Transactional;

import java.util.List;
import java.util.UUID;

@GraphQLApi
@ApplicationScoped
public class ProjectManagerMutationResolver {

    private static final Logger LOG = Logger.getLogger(ProjectManagerMutationResolver.class);

    @Inject AuthContext authContext;

    @Inject AuthClient authClient;

    @Inject JsonWebToken jwt;
    @Inject UserService UserService;

    private static final HttpClient httpClient = HttpClient.newHttpClient();

    @Inject ApproveAlertPort approveAlertPort;
    @Inject CancelAlertPort cancelAlertPort;
    @Inject ApproveJustificationPort approveJustificationPort;
    @Inject RejectJustificationPort rejectJustificationPort;

    @Inject CreateProjectUseCase createProjectUseCase;
    @Inject UpdateProjectUseCase updateProjectUseCase;
    @Inject DeleteProjectUseCase deleteProjectUseCase;

    @Inject AddProjectMemberUseCase addProjectMemberUseCase;
    @Inject UpdateProjectMemberUseCase updateProjectMemberUseCase;
    @Inject DeleteProjectMemberUseCase deleteProjectMemberUseCase;

    @Inject AssignSchedulePort assignSchedulePort;
    @Inject DeleteSchedulePort deleteSchedulePort;

    @Inject RequestObservationPort requestObservationPort;

    @Inject CreateNotificationUseCase createNotificationUseCase;
    @Inject DeleteNotificationUseCase deleteNotificationUseCase;
    @Inject com.smms.assistance.application.usecase.CreateAlertUseCase createAlertUseCase;

    @Inject com.smms.assistance.application.usecase.CreateShiftTemplateUseCase createShiftTemplateUseCase;

    @Inject com.smms.assistance.application.in.RequestVacationPort requestVacationPort;
    @Inject com.smms.assistance.application.in.ApproveVacationPort approveVacationPort;
    @Inject com.smms.assistance.application.in.RejectVacationPort rejectVacationPort;
    @Inject com.smms.assistance.application.in.CancelVacationPort cancelVacationPort;

    @Inject HolidayRepository holidayRepository;
    @Inject com.smms.assistance.infrastructure.repository.CollectionRepository collectionRepository;
    @Inject com.smms.assistance.infrastructure.repository.CollectionItemRepository collectionItemRepository;
    @Inject com.smms.assistance.infrastructure.repository.ProjectCollectionRepository projectCollectionRepository;

    @Inject UserService userService;

    private void validateProjectRoles(ProjectInput input) {
        List<UUID> userIdsToValidate = new java.util.ArrayList<>();
        if (input.responsibleId != null) {
            userIdsToValidate.add(input.responsibleId);
        }
        if (input.members != null) {
            for (ProjectMemberInput member : input.members) {
                if (member.userId != null && !userIdsToValidate.contains(member.userId)) {
                    userIdsToValidate.add(member.userId);
                }
            }
        }

        if (userIdsToValidate.isEmpty()) {
            return;
        }

        List<UserOutput> fetchedUsers = userService.fetchUsersBatch(userIdsToValidate, jwt.getRawToken());
        java.util.Map<UUID, UserOutput> userMap = new java.util.HashMap<>();
        if (fetchedUsers != null) {
            for (UserOutput u : fetchedUsers) {
                if (u != null && u.id != null) {
                    userMap.put(u.id, u);
                }
            }
        }

        if (input.responsibleId != null) {
            UserOutput responsibleUser = userMap.get(input.responsibleId);
            if (responsibleUser == null || responsibleUser.roleCode == null || !responsibleUser.roleCode.equals("PROJECT_MANAGER")) {
                System.out.println("❌ [PM Backend] Error: User " + input.responsibleId + " does not have the PROJECT_MANAGER role.");
                throw new IllegalArgumentException("Responsible must be a user with role PROJECT_MANAGER");
            }
        }

        if (input.members != null) {
            for (ProjectMemberInput member : input.members) {
                if (input.responsibleId != null && member.userId.equals(input.responsibleId)) {
                    continue;
                }
                UserOutput user = userMap.get(member.userId);
                if (user == null || user.roleCode == null || (!user.roleCode.equals("TEAM_MEMBER") && !user.roleCode.equals("USER"))) {
                    System.out.println("❌ [PM Backend] Error: User " + member.userId + " does not have the TEAM_MEMBER role.");
                    throw new IllegalArgumentException("Project members must have role TEAM_MEMBER or USER");
                }
            }
        }
    }

    // =========================
    // HELPER USER FETCH
    // =========================
    private UserOutput fetchUser(UUID userId) {
        return userService.fetchUser(userId, jwt.getRawToken());
    }

    // =========================
    // ALERTS
    // =========================

    @Mutation("approveAlert")
    @RolesAllowed({"ADMIN", "PROJECT_MANAGER"})
    public AlertOutput approveAlert(@Name("alertId") UUID alertId) {

        authContext.assertAdminOrProjectManager();

        System.out.println("🚨 Approving alert: " + alertId);

        return DtoMapper.toAlertOutput(
                approveAlertPort.execute(alertId, authContext.getUserId())
        );
    }

    @Mutation("cancelAlert")
    @RolesAllowed({"ADMIN", "PROJECT_MANAGER"})
    public AlertOutput cancelAlert(@Name("alertId") UUID alertId) {

        authContext.assertAdminOrProjectManager();

        System.out.println("🚫 Cancelling alert: " + alertId);

        return DtoMapper.toAlertOutput(
                cancelAlertPort.execute(alertId, authContext.getUserId())
        );
    }

    @Mutation("createAlert")
    @RolesAllowed({"PROJECT_MANAGER", "ADMIN", "TEAM_MEMBER"})
    public AlertOutput createAlert(
            @Name("userId") UUID userId,
            @Name("projectId") UUID projectId,
            @Name("type") String type,
            @Name("detail") String detail,
            @Name("latitude") Double latitude,
            @Name("longitude") Double longitude
    ) {
        String role = authContext.getRole();
        if (!"ADMIN".equals(role)) {
            // Un PM puede crear alertas para sus proyectos
            if ("PROJECT_MANAGER".equals(role)) {
                assertMemberManagementPermission(projectId);
            } 
            // Un TEAM_MEMBER solo puede crear alertas para sí mismo y sus proyectos
            else if ("TEAM_MEMBER".equals(role)) {
                if (!authContext.getUserId().equals(userId)) {
                    throw new SecurityException("Access denied: Team members can only create alerts for themselves");
                }
                long count = ProjectMember.count("projectId = ?1 and userId = ?2", projectId, authContext.getUserId());
                if (count == 0) {
                    throw new SecurityException("Access denied: You do not belong to this project");
                }
            }
        }
        
        System.out.println("🚀 GraphQL Mutation CreateAlert received -> userId: " + userId + ", projectId: " + projectId + ", type: " + type);
        try {
            var alert = createAlertUseCase.execute(userId, projectId, type, detail, latitude, longitude);
            System.out.println("✅ Alert saved in PostgreSQL database successfully -> alertId: " + alert.getId());
            return DtoMapper.toAlertOutput(alert);
        } catch (Exception e) {
            System.out.println("❌ Alert persistence failure: " + e.getMessage());
            throw e;
        }
    }

    // =========================
    // JUSTIFICATIONS
    // =========================

    @Mutation("approveJustification")
    @RolesAllowed({"ADMIN", "PROJECT_MANAGER"})
    public JustificationOutput approveJustification(
            @Name("justificationId") UUID justificationId,
            @Name("comment") String comment
    ) {

        authContext.assertAdminOrProjectManager();

        System.out.println("✔ Approving justification: " + justificationId);

        return DtoMapper.toJustificationOutput(
                approveJustificationPort.execute(
                        justificationId,
                        comment,
                        authContext.getUserId()
                )
        );
    }

    @Mutation("rejectJustification")
    @RolesAllowed({"ADMIN", "PROJECT_MANAGER"})
    public JustificationOutput rejectJustification(
            @Name("justificationId") UUID justificationId,
            @Name("comment") String comment
    ) {

        authContext.assertAdminOrProjectManager();

        System.out.println("❌ Rejecting justification: " + justificationId);

        return DtoMapper.toJustificationOutput(
                rejectJustificationPort.execute(
                        justificationId,
                        comment,
                        authContext.getUserId()
                )
        );
    }

    @Mutation("requestObservation")
    @RolesAllowed({"ADMIN", "PROJECT_MANAGER"})
    public JustificationOutput requestObservation(
            @Name("justificationId") UUID justificationId,
            @Name("comment") String comment
    ) {
        authContext.assertAdminOrProjectManager();

        System.out.println("🔍 Requesting observation for justification: " + justificationId);

        return DtoMapper.toJustificationOutput(
                requestObservationPort.execute(
                        justificationId,
                        comment,
                        authContext.getUserId()
                )
        );
    }

    // =========================
    // PROJECTS
    // =========================

    @Mutation("createProject")
    @RolesAllowed("ADMIN")
    public ProjectOutput createProject(@Name("input") ProjectInput input) {

        authContext.assertAdmin();

        System.out.println("📁 Creating project: " + input.name);

        validateProjectRoles(input);

        var result = createProjectUseCase.execute(input);
        emitProjectEvent("PROJECT_CREATED", result.id);
        return toProjectOutput(result);
    }

    private void emitProjectEvent(String eventType, UUID projectId) {
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                LOG.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                LOG.info("📤 [PROJECT EVENT] EMITTING: " + eventType + " | projectId=" + projectId);

                // Get project members to invalidate only their caches
                List<ProjectMember> projectMembers = ProjectMember.list("projectId = ?1", projectId);
                List<UUID> memberUserIds = projectMembers.stream()
                        .map(m -> m.userId)
                        .collect(java.util.stream.Collectors.toList());

                LOG.info("📤 [PROJECT EVENT] Affected users: " + memberUserIds.size());

                JsonObject payload = new JsonObject();
                payload.put("eventType", eventType);
                payload.put("projectId", projectId.toString());
                payload.put("userIds", new io.vertx.core.json.JsonArray(memberUserIds.stream().map(Object::toString).collect(java.util.stream.Collectors.toList())));

                String defaultUrl = "http://astc-svc-backend-assistance-service.astc-app.svc.cluster.local:8082";
                String assistanceUrl = System.getenv().getOrDefault("ASSISTANCE_URL", defaultUrl);
                if ("http://assistance-service:8082".equals(assistanceUrl)) {
                    assistanceUrl = defaultUrl;
                }

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(assistanceUrl + "/api/cache/invalidate-projects"))
                        .header("Content-Type", "application/json")
                        .timeout(java.time.Duration.ofSeconds(3))
                        .POST(HttpRequest.BodyPublishers.ofString(payload.encode()))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                LOG.info("✅ [PROJECT EVENT] Cache invalidation response: " + response.statusCode());
                LOG.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            } catch (Exception e) {
                LOG.error("❌ [PROJECT EVENT] FAILED to emit: " + e.getMessage());
            }
        });
    }

    @Mutation("updateProject")
    @RolesAllowed({"ADMIN", "PROJECT_MANAGER"})
    public ProjectOutput updateProject(
            @Name("id") UUID id,
            @Name("input") ProjectInput input
    ) {

        authContext.assertAdminOrProjectManager();
        
        // [IDOR PATCH] Verify the PM is actually assigned to this project
        assertMemberManagementPermission(id);

        System.out.println("✏ Updating project: " + id);

        validateProjectRoles(input);

        var result = updateProjectUseCase.execute(id, input);
        emitProjectEvent("PROJECT_UPDATED", id);
        return toProjectOutput(result);
    }

    @Mutation("deleteProject")
    @RolesAllowed("ADMIN")
    public boolean deleteProject(@Name("id") UUID id) {

        authContext.assertAdmin();

        System.out.println("🗑 Deleting project: " + id);

        boolean deleted = deleteProjectUseCase.execute(id);
        if (deleted) {
            emitProjectEvent("PROJECT_DELETED", id);
        }
        return deleted;
    }

    // =========================
    // PROJECT MEMBERS (🔥 USER ENRICHED)
    // =========================

    private void assertMemberManagementPermission(UUID projectId) {
        String role = authContext.getRole();
        if ("ADMIN".equals(role)) {
            System.out.println("🛡️ [Security] ADMIN bypass granted for projectId: " + projectId);
            return;
        }
        
        if ("PROJECT_MANAGER".equals(role)) {
            long count = ProjectMember.count("projectId = ?1 and userId = ?2", projectId, authContext.getUserId());
            if (count == 0) {
                System.out.println("🚨 [Security IDOR Blocked] PM " + authContext.getUserId() + " attempted to access unauthorized projectId: " + projectId);
                throw new SecurityException("Access denied: You are not assigned to this project");
            }
            System.out.println("✅ [Security] PM Ownership validated for project: " + projectId);
            return;
        }
        
        System.out.println("🚨 [Security Blocked] Unauthorized role " + role + " attempted to access projectId: " + projectId);
        throw new SecurityException("Access denied: Insufficient permissions");
    }

    private void assertUserManagementPermission(UUID targetUserId) {
        String role = authContext.getRole();
        if ("ADMIN".equals(role)) {
            System.out.println("🛡️ [Security] ADMIN bypass granted for user query: " + targetUserId);
            return;
        }
        
        if ("PROJECT_MANAGER".equals(role)) {
            if (targetUserId == null) return;
            long count = ProjectMember.count("projectId in (select m2.projectId from ProjectMember m2 where m2.userId = ?1) and userId = ?2", authContext.getUserId(), targetUserId);
            if (count == 0) {
                System.out.println("🚨 [Security IDOR Blocked - Cross-Validation] PM " + authContext.getUserId() + " attempted to query unauthorized targetUserId: " + targetUserId);
                throw new SecurityException("Access denied: You do not manage this user");
            }
            System.out.println("✅ [Security - Cross-Validation] PM ownership verified for targetUserId: " + targetUserId);
            return;
        }
        
        System.out.println("🚨 [Security Blocked] Unauthorized role " + role + " attempted cross-validation for user: " + targetUserId);
        throw new SecurityException("Access denied: Insufficient permissions");
    }

    @Mutation("addProjectMember")
    @RolesAllowed({"ADMIN", "PROJECT_MANAGER"})
    public ProjectMemberOutput addProjectMember(@Name("input") ProjectMemberInput input) {

        assertMemberManagementPermission(input.projectId);

        System.out.println("➕ Adding project member: " + input.userId);

        // Enforce business rule: Project members must be TEAM_MEMBER or USER
        UserOutput user = fetchUser(input.userId);
        if (user == null || user.roleCode == null || (!user.roleCode.equals("TEAM_MEMBER") && !user.roleCode.equals("USER"))) {
            System.out.println("❌ [PM Backend] Error: User " + input.userId + " does not have the TEAM_MEMBER role.");
            throw new IllegalArgumentException("Project members must have role TEAM_MEMBER or USER");
        }

        ProjectMember member = addProjectMemberUseCase.execute(input);
        emitProjectEvent("PROJECT_MEMBER_ADDED", input.projectId);
        return toProjectMemberOutput(member);
    }

    @Mutation("updateProjectMember")
    @RolesAllowed({"ADMIN", "PROJECT_MANAGER"})
    public ProjectMemberOutput updateProjectMember(
            @Name("id") UUID id,
            @Name("role") String role
    ) {
        ProjectMember existing = ProjectMember.findById(id);
        if (existing == null) throw new IllegalArgumentException("Member not found");
        
        assertMemberManagementPermission(existing.projectId);

        System.out.println("✏ Updating project member: " + id);

        ProjectMember member = updateProjectMemberUseCase.execute(id, role);
        emitProjectEvent("PROJECT_MEMBER_ROLE_CHANGED", existing.projectId);
        return toProjectMemberOutput(member);
    }

    @Mutation("deleteProjectMember")
    @RolesAllowed({"ADMIN", "PROJECT_MANAGER"})
    public boolean deleteProjectMember(@Name("id") UUID id) {
        ProjectMember existing = ProjectMember.findById(id);
        if (existing == null) throw new IllegalArgumentException("Member not found");
        
        assertMemberManagementPermission(existing.projectId);

        System.out.println("🗑 Deleting project member: " + id);

        boolean deleted = deleteProjectMemberUseCase.execute(id);
        if (deleted) {
            emitProjectEvent("PROJECT_MEMBER_REMOVED", existing.projectId);
        }
        return deleted;
    }

    // =========================
    // SCHEDULE OVERRIDES
    // =========================

    @Mutation("assignSchedule")
    @RolesAllowed({"ADMIN", "PROJECT_MANAGER"})
    public ScheduleOutput assignSchedule(@Name("input") ScheduleInput input) {
        authContext.assertAdminOrProjectManager();
        
        // [IDOR PATCH] Verify PM owns the project
        assertMemberManagementPermission(input.projectId);

        System.out.println("🕒 Assigning schedule override: userId=" + input.userId + ", projectId=" + input.projectId);

        var entity = assignSchedulePort.execute(
                input.userId,
                input.projectId,
                input.shiftType,
                input.workStartTime,
                input.workEndTime,
                input.graceMinutes,
                input.mondayStart,
                input.mondayEnd,
                input.tuesdayStart,
                input.tuesdayEnd,
                input.wednesdayStart,
                input.wednesdayEnd,
                input.thursdayStart,
                input.thursdayEnd,
                input.fridayStart,
                input.fridayEnd,
                input.saturdayStart,
                input.saturdayEnd,
                input.sundayStart,
                input.sundayEnd,
                input.rotationWorkDays,
                input.rotationRestDays,
                input.rotationShiftStartTime,
                input.rotationShiftEndTime,
                input.validFrom,
                input.validUntil,
                input.absenceCutoffTime,
                input.timezone
        );

        return ScheduleOutput.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .projectId(entity.getProjectId())
                .workStartTime(entity.getWorkStartTime() != null ? entity.getWorkStartTime().toString() : null)
                .workEndTime(entity.getWorkEndTime() != null ? entity.getWorkEndTime().toString() : null)
                .graceMinutes(entity.getGraceMinutes())
                .shiftType(entity.getShiftType())
                .mondayStart(entity.getMondayStart() != null ? entity.getMondayStart().toString() : null)
                .mondayEnd(entity.getMondayEnd() != null ? entity.getMondayEnd().toString() : null)
                .tuesdayStart(entity.getTuesdayStart() != null ? entity.getTuesdayStart().toString() : null)
                .tuesdayEnd(entity.getTuesdayEnd() != null ? entity.getTuesdayEnd().toString() : null)
                .wednesdayStart(entity.getWednesdayStart() != null ? entity.getWednesdayStart().toString() : null)
                .wednesdayEnd(entity.getWednesdayEnd() != null ? entity.getWednesdayEnd().toString() : null)
                .thursdayStart(entity.getThursdayStart() != null ? entity.getThursdayStart().toString() : null)
                .thursdayEnd(entity.getThursdayEnd() != null ? entity.getThursdayEnd().toString() : null)
                .fridayStart(entity.getFridayStart() != null ? entity.getFridayStart().toString() : null)
                .fridayEnd(entity.getFridayEnd() != null ? entity.getFridayEnd().toString() : null)
                .saturdayStart(entity.getSaturdayStart() != null ? entity.getSaturdayStart().toString() : null)
                .saturdayEnd(entity.getSaturdayEnd() != null ? entity.getSaturdayEnd().toString() : null)
                .sundayStart(entity.getSundayStart() != null ? entity.getSundayStart().toString() : null)
                .sundayEnd(entity.getSundayEnd() != null ? entity.getSundayEnd().toString() : null)
                .rotationWorkDays(entity.getRotationWorkDays())
                .rotationRestDays(entity.getRotationRestDays())
                .rotationShiftStartTime(entity.getRotationShiftStartTime() != null ? entity.getRotationShiftStartTime().toString() : null)
                .rotationShiftEndTime(entity.getRotationShiftEndTime() != null ? entity.getRotationShiftEndTime().toString() : null)
                .validFrom(entity.getValidFrom() != null ? entity.getValidFrom().toString() : null)
                .validUntil(entity.getValidUntil() != null ? entity.getValidUntil().toString() : null)
                .absenceCutoffTime(entity.getAbsenceCutoffTime() != null ? entity.getAbsenceCutoffTime().toString() : null)
                .timezone(entity.getTimezone())
                .build();
    }

    @Mutation("deleteSchedule")
    @RolesAllowed({"ADMIN", "PROJECT_MANAGER"})
    public boolean deleteSchedule(@Name("userId") UUID userId, @Name("projectId") UUID projectId) {
        authContext.assertAdminOrProjectManager();
        
        // [IDOR PATCH] Verify PM owns the project
        assertMemberManagementPermission(projectId);

        System.out.println("🗑 Deleting schedule override: userId=" + userId + ", projectId=" + projectId);

        return deleteSchedulePort.execute(userId, projectId);
    }

    // =========================
    // NOTIFICATIONS
    // =========================

    @Mutation("createNotification")
    @RolesAllowed({"ADMIN", "PROJECT_MANAGER"})
    public NotificationOutput createNotification(@Name("input") NotificationInput input) {

        authContext.assertAdminOrProjectManager();
        
        if (input != null && input.userId != null) {
            assertUserManagementPermission(input.userId);
        }

        System.out.println("📣 Creating notification for user: " + input.userId);

        Notification notification = createNotificationUseCase.execute(
                input.userId,
                input.type,
                input.severity,
                input.title,
                input.message,
                input.referenceType,
                input.referenceId
        );

        NotificationOutput output = toNotificationOutput(notification);

        output.user = fetchUser(input.userId);

        return output;
    }

    // =========================
    // MAPPERS
    // =========================

    private ProjectOutput toProjectOutput(Project project) {

        ProjectOutput output = new ProjectOutput();

        output.id = project.id;
        output.name = project.name;
        output.description = project.description;
        output.status = project.status;
        output.startDate = project.startDate;
        output.endDate = project.endDate;
        output.budget = project.budget;
        output.currency = project.currency;

        output.workStartTime = project.workStartTime != null ? project.workStartTime.toString() : null;
        output.workEndTime = project.workEndTime != null ? project.workEndTime.toString() : null;
        output.graceMinutes = project.graceMinutes;
        output.timezone = project.timezone;

        output.responsibleId = project.responsibleId;
        output.absenceCutoffTime = project.absenceCutoffTime != null ? project.absenceCutoffTime.toString() : null;
        output.vacationEligibilityDays = project.vacationEligibilityDays;

        try {
            java.util.List<com.smms.assistance.domain.entity.HolidayEntity> holidayEntities = holidayRepository.findByTargetId(project.id);
            output.holidays = holidayEntities.stream()
                    .map(h -> h.getDate().toString())
                    .collect(java.util.stream.Collectors.toList());
        } catch (Exception e) {
            System.out.println("⚠️ [PM Backend] Error populating holidays: " + e.getMessage());
        }

        output.createdAt = project.createdAt;

        return output;
    }

    // =========================
    // SHIFT TEMPLATES
    // =========================

    @Mutation("createShiftTemplate")
    @RolesAllowed({"ADMIN", "PROJECT_MANAGER"})
    public ShiftTemplateOutput createShiftTemplate(@Name("input") ShiftTemplateInput input) {
        authContext.assertAdminOrProjectManager();
        
        // [IDOR PATCH] Verify PM owns the project
        assertMemberManagementPermission(input.projectId);
        
        var entity = createShiftTemplateUseCase.execute(
                input.projectId, input.name, input.shiftType,
                input.workStartTime, input.workEndTime, input.graceMinutes,
                input.mondayStart, input.mondayEnd,
                input.tuesdayStart, input.tuesdayEnd,
                input.wednesdayStart, input.wednesdayEnd,
                input.thursdayStart, input.thursdayEnd,
                input.fridayStart, input.fridayEnd,
                input.saturdayStart, input.saturdayEnd,
                input.sundayStart, input.sundayEnd,
                input.rotationWorkDays, input.rotationRestDays,
                input.rotationShiftStartTime, input.rotationShiftEndTime,
                input.validFrom, input.validUntil,
                input.absenceCutoffTime, input.timezone
        );
        return toShiftTemplateOutput(entity);
    }

    @Mutation("deleteShiftTemplate")
    @RolesAllowed({"ADMIN", "PROJECT_MANAGER"})
    @Transactional
    public boolean deleteShiftTemplate(@Name("id") UUID id) {
        authContext.assertAdminOrProjectManager();
        ShiftTemplateEntity entity = ShiftTemplateEntity.findById(id);
        if (entity == null) {
            throw new IllegalArgumentException("La plantilla de horario no existe.");
        }
        
        // [IDOR PATCH] Verify PM owns the project
        assertMemberManagementPermission(entity.getProjectId());
        
        ShiftTemplateEntity.getEntityManager()
                .createNativeQuery("UPDATE schedule_overrides SET shift_template_id = NULL WHERE shift_template_id = ?1")
                .setParameter(1, id)
                .executeUpdate();
        
        entity.delete();
        return true;
    }

    private ShiftTemplateOutput toShiftTemplateOutput(ShiftTemplateEntity entity) {
        return ShiftTemplateOutput.builder()
                .id(entity.getId())
                .projectId(entity.getProjectId())
                .name(entity.getName())
                .shiftType(entity.getShiftType())
                .workStartTime(entity.getWorkStartTime() != null ? entity.getWorkStartTime().toString() : null)
                .workEndTime(entity.getWorkEndTime() != null ? entity.getWorkEndTime().toString() : null)
                .graceMinutes(entity.getGraceMinutes())
                .mondayStart(entity.getMondayStart() != null ? entity.getMondayStart().toString() : null)
                .mondayEnd(entity.getMondayEnd() != null ? entity.getMondayEnd().toString() : null)
                .tuesdayStart(entity.getTuesdayStart() != null ? entity.getTuesdayStart().toString() : null)
                .tuesdayEnd(entity.getTuesdayEnd() != null ? entity.getTuesdayEnd().toString() : null)
                .wednesdayStart(entity.getWednesdayStart() != null ? entity.getWednesdayStart().toString() : null)
                .wednesdayEnd(entity.getWednesdayEnd() != null ? entity.getWednesdayEnd().toString() : null)
                .thursdayStart(entity.getThursdayStart() != null ? entity.getThursdayStart().toString() : null)
                .thursdayEnd(entity.getThursdayEnd() != null ? entity.getThursdayEnd().toString() : null)
                .fridayStart(entity.getFridayStart() != null ? entity.getFridayStart().toString() : null)
                .fridayEnd(entity.getFridayEnd() != null ? entity.getFridayEnd().toString() : null)
                .saturdayStart(entity.getSaturdayStart() != null ? entity.getSaturdayStart().toString() : null)
                .saturdayEnd(entity.getSaturdayEnd() != null ? entity.getSaturdayEnd().toString() : null)
                .sundayStart(entity.getSundayStart() != null ? entity.getSundayStart().toString() : null)
                .sundayEnd(entity.getSundayEnd() != null ? entity.getSundayEnd().toString() : null)
                .rotationWorkDays(entity.getRotationWorkDays())
                .rotationRestDays(entity.getRotationRestDays())
                .rotationShiftStartTime(entity.getRotationShiftStartTime() != null ? entity.getRotationShiftStartTime().toString() : null)
                .rotationShiftEndTime(entity.getRotationShiftEndTime() != null ? entity.getRotationShiftEndTime().toString() : null)
                .validFrom(entity.getValidFrom() != null ? entity.getValidFrom().toString() : null)
                .validUntil(entity.getValidUntil() != null ? entity.getValidUntil().toString() : null)
                .absenceCutoffTime(entity.getAbsenceCutoffTime() != null ? entity.getAbsenceCutoffTime().toString() : null)
                .timezone(entity.getTimezone())
                .createdAt(entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null)
                .build();
    }

    // =========================
    // VACATIONS
    // =========================

    @Mutation("requestVacation")
    @RolesAllowed({"TEAM_MEMBER", "PROJECT_MANAGER", "ADMIN"})
    public VacationRequestOutput requestVacation(@Name("input") VacationRequestInput input) {
        try {
            UUID currentUserId = authContext.getUserId();
            var entity = requestVacationPort.execute(
                    currentUserId, input.projectId, input.startDate, input.endDate, input.businessDays, input.comment
            );
            return VacationRequestOutput.builder()
                    .id(entity.getId())
                    .userId(entity.getUserId())
                    .projectId(entity.getProjectId())
                    .startDate(entity.getStartDate() != null ? entity.getStartDate().toString() : null)
                    .endDate(entity.getEndDate() != null ? entity.getEndDate().toString() : null)
                    .businessDays(entity.getBusinessDays())
                    .status(entity.getStatus())
                    .comment(entity.getComment())
                    .createdAt(entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null)
                    .build();
        } catch (IllegalStateException e) {
            LOG.warnf("[VACATION] Solicitud rechazada: userId=%s, reason=%s", input.userId, e.getMessage());
            throw new IllegalArgumentException(e.getMessage());
        } catch (Exception e) {
            LOG.errorf(e, "[VACATION] Error al procesar solicitud: userId=%s, projectId=%s, start=%s, end=%s",
                    input.userId, input.projectId, input.startDate, input.endDate);
            throw e;
        }
    }

    @Mutation("approveVacation")
    @RolesAllowed({"ADMIN", "PROJECT_MANAGER"})
    public VacationRequestOutput approveVacation(@Name("requestId") UUID requestId, @Name("discountDays") Integer discountDays) {
        authContext.assertAdminOrProjectManager();
        var entity = approveVacationPort.execute(requestId, authContext.getUserId(), discountDays);
        return toVacationOutput(entity);
    }

    @Mutation("rejectVacation")
    @RolesAllowed({"ADMIN", "PROJECT_MANAGER"})
    public VacationRequestOutput rejectVacation(@Name("requestId") UUID requestId, @Name("comment") String comment) {
        authContext.assertAdminOrProjectManager();
        var entity = rejectVacationPort.execute(requestId, authContext.getUserId(), comment);
        return toVacationOutput(entity);
    }

    @Mutation("cancelVacation")
    @RolesAllowed({"TEAM_MEMBER", "PROJECT_MANAGER", "ADMIN"})
    public VacationRequestOutput cancelVacation(@Name("requestId") UUID requestId) {
        var entity = cancelVacationPort.execute(requestId, authContext.getUserId());
        return toVacationOutput(entity);
    }

    private VacationRequestOutput toVacationOutput(com.smms.assistance.domain.entity.VacationRequestEntity entity) {
        return VacationRequestOutput.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .projectId(entity.getProjectId())
                .startDate(entity.getStartDate() != null ? entity.getStartDate().toString() : null)
                .endDate(entity.getEndDate() != null ? entity.getEndDate().toString() : null)
                .businessDays(entity.getBusinessDays())
                .status(entity.getStatus())
                .comment(entity.getComment())
                .reviewedBy(entity.getReviewedBy() != null ? entity.getReviewedBy().toString() : null)
                .reviewedAt(entity.getReviewedAt() != null ? entity.getReviewedAt().toString() : null)
                .createdAt(entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null)
                .build();
    }

    private ProjectMemberOutput toProjectMemberOutput(ProjectMember member) {

        ProjectMemberOutput output = new ProjectMemberOutput();

        output.id = member.id;
        output.projectId = member.projectId;
        output.userId = member.userId;
        output.role = member.role;
        output.createdAt = member.createdAt;

        return output;
    }

    private NotificationOutput toNotificationOutput(Notification notification) {

        NotificationOutput output = new NotificationOutput();

        output.id = notification.id;
        output.userId = notification.userId;
        output.type = notification.type;
        output.severity = notification.severity;
        output.title = notification.title;
        output.message = notification.message;
        output.referenceType = notification.referenceType;
        output.referenceId = notification.referenceId;
        output.createdAt = notification.createdAt;

        return output;
    }

    // =========================
    // HOLIDAYS
    // =========================

    @Mutation("createHoliday")
    @RolesAllowed({"ADMIN", "PROJECT_MANAGER"})
    public HolidayOutput createHoliday(@Name("input") HolidayInput input) {
        
        if ("PROJECT_MANAGER".equals(authContext.getRole()) && input.targetId != null) {
            // [IDOR PATCH] Verify PM owns the project
            assertMemberManagementPermission(input.targetId);
        }
        
        HolidayEntity entity = HolidayEntity.builder()
                .id(UUID.randomUUID())
                .type(input.type != null ? input.type : "PROJECT")
                .targetId(input.targetId)
                .date(input.date)
                .name(input.name)
                .createdAt(java.time.LocalDateTime.now())
                .build();
        holidayRepository.persist(entity);
        return HolidayOutput.builder()
                .id(entity.getId())
                .type(entity.getType())
                .targetId(entity.getTargetId())
                .date(entity.getDate())
                .name(entity.getName())
                .build();
    }

    @Mutation("deleteHoliday")
    @RolesAllowed({"ADMIN", "PROJECT_MANAGER"})
    public boolean deleteHoliday(@Name("id") UUID id) {
        authContext.assertAdminOrProjectManager();
        holidayRepository.deleteById(id);
        return true;
    }

    @Mutation("deleteAllHolidays")
    @RolesAllowed({"ADMIN", "PROJECT_MANAGER"})
    public boolean deleteAllHolidays(@Name("targetId") UUID targetId, @Name("type") String type) {
        authContext.assertAdminOrProjectManager();
        
        if ("PROJECT_MANAGER".equals(authContext.getRole()) && targetId != null) {
            // [IDOR PATCH] Verify PM owns the project
            assertMemberManagementPermission(targetId);
        }
        
        holidayRepository.deleteAllHolidays(targetId, type);
        return true;
    }

    @Mutation("updateAdminSetting")
    @RolesAllowed("ADMIN")
    @Transactional
    public boolean updateAdminSetting(@Name("key") String key, @Name("value") String value) {
        authContext.assertAdmin();
        var em = com.smms.assistance.domain.entity.HolidayEntity.getEntityManager();
        long count = em.createNativeQuery("update admin_settings set value = ?1, updated_at = now() where key = ?2")
                .setParameter(1, value)
                .setParameter(2, key)
                .executeUpdate();
        if (count == 0) {
            em.createNativeQuery("insert into admin_settings (key, value, updated_at) values (?1, ?2, now())")
                    .setParameter(1, key)
                    .setParameter(2, value)
                    .executeUpdate();
        }
        return true;
    }

    // =========================
    // COLLECTIONS (HOLIDAYS)
    // =========================

    @Mutation("createCollection")
    @RolesAllowed({"ADMIN", "PROJECT_MANAGER"})
    public CollectionOutput createCollection(
            @Name("input") CollectionInput input,
            @Name("items") java.util.List<com.smms.assistance.infrastructure.controller.graphql.dto.CollectionItemInput> items) {
        
        authContext.assertAdminOrProjectManager();
        
        CollectionEntity collection = CollectionEntity.builder()
                .id(UUID.randomUUID())
                .name(input.getName())
                .scope(input.getScope())
                .ownerId(input.getOwnerId())
                .createdAt(java.time.LocalDateTime.now())
                .build();
                
        collectionRepository.persist(collection);

        // Si es proyecto, vincularlo automáticamente
        if ("PROJECT".equals(input.getScope()) && input.getOwnerId() != null) {
            ProjectCollectionEntity pc = ProjectCollectionEntity.builder()
                    .projectId(input.getOwnerId())
                    .collectionId(collection.getId())
                    .build();
            projectCollectionRepository.persist(pc);
        }

        if (items != null) {
            java.util.Set<java.time.LocalDate> uniqueDates = new java.util.HashSet<>();
            for (com.smms.assistance.infrastructure.controller.graphql.dto.CollectionItemInput itemInput : items) {
                if (uniqueDates.add(itemInput.getDate())) {
                    CollectionItemEntity item = CollectionItemEntity.builder()
                            .id(UUID.randomUUID())
                            .collectionId(collection.getId())
                            .date(itemInput.getDate())
                            .name(itemInput.getName() != null && !itemInput.getName().isBlank() ? itemInput.getName() : input.getName())
                            .createdAt(java.time.LocalDateTime.now())
                            .build();
                    collectionItemRepository.persist(item);
                }
            }
        }

        return CollectionOutput.builder()
                .id(collection.getId())
                .name(collection.getName())
                .scope(collection.getScope())
                .ownerId(collection.getOwnerId())
                .createdAt(collection.getCreatedAt())
                .build();
    }

    @Mutation("deleteCollection")
    @RolesAllowed({"ADMIN", "PROJECT_MANAGER"})
    public boolean deleteCollection(@Name("id") UUID id) {
        authContext.assertAdminOrProjectManager();
        collectionItemRepository.deleteByCollectionId(id);
        collectionRepository.deleteById(id);
        return true;
    }

    @Mutation("deleteCollectionItem")
    @RolesAllowed({"ADMIN", "PROJECT_MANAGER"})
    public boolean deleteCollectionItem(@Name("id") UUID id) {
        authContext.assertAdminOrProjectManager();
        collectionItemRepository.deleteById(id);
        return true;
    }

    @Mutation("linkProjectToCollection")
    @RolesAllowed({"ADMIN", "PROJECT_MANAGER"})
    public boolean linkProjectToCollection(@Name("projectId") UUID projectId, @Name("collectionId") UUID collectionId) {
        authContext.assertAdminOrProjectManager();
        projectCollectionRepository.unlinkAllFromProject(projectId);
        ProjectCollectionEntity pc = ProjectCollectionEntity.builder()
                .projectId(projectId)
                .collectionId(collectionId)
                .build();
        projectCollectionRepository.persist(pc);
        return true;
    }

    @Mutation("unlinkProjectFromCollection")
    @RolesAllowed({"ADMIN", "PROJECT_MANAGER"})
    public boolean unlinkProjectFromCollection(@Name("projectId") UUID projectId, @Name("collectionId") UUID collectionId) {
        authContext.assertAdminOrProjectManager();
        projectCollectionRepository.unlinkProjectFromCollection(projectId, collectionId);
        return true;
    }

    @Mutation("addDatesToCollection")
    @RolesAllowed({"ADMIN", "PROJECT_MANAGER"})
    public boolean addDatesToCollection(@Name("collectionId") UUID collectionId, @Name("dates") java.util.List<java.time.LocalDate> dates, @Name("name") String name) {
        authContext.assertAdminOrProjectManager();
        
        java.util.List<CollectionItemEntity> existing = collectionItemRepository.findByCollectionId(collectionId);
        java.util.Set<java.time.LocalDate> existingDates = existing.stream()
            .map(CollectionItemEntity::getDate)
            .collect(java.util.stream.Collectors.toSet());

        for (java.time.LocalDate date : dates) {
            if (existingDates.contains(date)) {
                continue;
            }
            CollectionItemEntity entity = CollectionItemEntity.builder()
                    .id(UUID.randomUUID())
                    .collectionId(collectionId)
                    .date(date)
                    .name(name != null && !name.trim().isEmpty() ? name : "Feriado")
                    .createdAt(java.time.LocalDateTime.now())
                    .build();
            collectionItemRepository.persist(entity);
        }
        return true;
    }

    @Mutation("bulkcreateholidays")
    @RolesAllowed({"ADMIN", "PROJECT_MANAGER"})
    public java.util.List<HolidayOutput> bulkCreateHolidays(@Name("targetId") UUID targetId, @Name("dates") java.util.List<java.time.LocalDate> dates, @Name("name") String name, @Name("type") String type) {
        authContext.assertAdminOrProjectManager();
        java.util.List<HolidayOutput> outputs = new java.util.ArrayList<>();
        String holidayType = type != null ? type : (targetId == null ? "GLOBAL" : "PROJECT");
        
        java.util.List<HolidayEntity> existing = holidayRepository.findByTypeAndTargetId(holidayType, targetId);
        java.util.Set<java.time.LocalDate> existingDates = existing.stream()
            .map(HolidayEntity::getDate)
            .collect(java.util.stream.Collectors.toSet());

        for (java.time.LocalDate date : dates) {
            if (existingDates.contains(date)) {
                continue;
            }
            existingDates.add(date);
            HolidayEntity entity = HolidayEntity.builder()
                    .id(UUID.randomUUID())
                    .type(holidayType)
                    .targetId(targetId)
                    .date(date)
                    .name(name)
                    .createdAt(java.time.LocalDateTime.now())
                    .build();
            holidayRepository.persist(entity);
            outputs.add(HolidayOutput.builder()
                    .id(entity.getId())
                    .type(entity.getType())
                    .targetId(entity.getTargetId())
                    .date(entity.getDate())
                    .name(entity.getName())
                    .build());
        }
        return outputs;
    }

    @Mutation("importHolidaysFromCsv")
    @RolesAllowed({"ADMIN", "PROJECT_MANAGER"})
    public ImportHolidaysResultOutput importHolidaysFromCsv(
            @Name("csvContent") String csvContent,
            @Name("targetId") UUID targetId,
            @Name("type") String type,
            @Name("defaultName") String defaultName
    ) {
        authContext.assertAdminOrProjectManager();
        String holidayType = type != null ? type : (targetId == null ? "GLOBAL" : "PROJECT");
        String nameDefault = defaultName != null ? defaultName : "Feriado";

        java.util.List<HolidayEntity> existing = holidayRepository.findByTypeAndTargetId(holidayType, targetId);
        java.util.Set<java.time.LocalDate> existingDates = existing.stream()
            .map(HolidayEntity::getDate)
            .collect(java.util.stream.Collectors.toSet());

        java.util.List<HolidayOutput> created = new java.util.ArrayList<>();
        int skipped = 0;
        int errors = 0;

        String[] lines = csvContent.split("\\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;

            if (i == 0) {
                String lower = line.toLowerCase();
                if (lower.startsWith("date") || lower.startsWith("fecha") || lower.startsWith("feriado")) {
                    continue;
                }
            }

            try {
                String dateStr;
                String holidayName = nameDefault;

                if (line.contains(",")) {
                    String[] parts = line.split(",", 2);
                    dateStr = parts[0].trim();
                    if (parts.length > 1 && !parts[1].trim().isEmpty()) {
                        holidayName = parts[1].trim();
                    }
                } else {
                    dateStr = line;
                }

                if (dateStr.isEmpty()) continue;

                java.time.LocalDate date = java.time.LocalDate.parse(dateStr);

                if (existingDates.contains(date)) {
                    skipped++;
                    continue;
                }
                existingDates.add(date);

                HolidayEntity entity = HolidayEntity.builder()
                        .id(UUID.randomUUID())
                        .type(holidayType)
                        .targetId(targetId)
                        .date(date)
                        .name(holidayName)
                        .createdAt(java.time.LocalDateTime.now())
                        .build();
                holidayRepository.persist(entity);
                created.add(HolidayOutput.builder()
                        .id(entity.getId())
                        .type(entity.getType())
                        .targetId(entity.getTargetId())
                        .date(entity.getDate())
                        .name(entity.getName())
                        .build());
            } catch (Exception e) {
                errors++;
            }
        }

        return ImportHolidaysResultOutput.builder()
                .total(created.size() + skipped + errors)
                .created(created.size())
                .skipped(skipped)
                .errors(errors)
                .holidays(created)
                .build();
    }
}
