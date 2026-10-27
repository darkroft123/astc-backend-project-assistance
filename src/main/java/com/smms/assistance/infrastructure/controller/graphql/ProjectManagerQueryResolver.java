package com.smms.assistance.infrastructure.controller.graphql;

import com.smms.assistance.application.in.*;
import com.smms.assistance.application.usecase.*;
import com.smms.assistance.application.service.ScheduleEvaluationService;

import com.smms.assistance.domain.entity.*;
import com.smms.assistance.domain.entity.ProjectMember;
import com.smms.assistance.domain.entity.ScheduleEntity;
import com.smms.assistance.domain.entity.ShiftTemplateEntity;

import com.smms.assistance.infrastructure.client.AuthClient;
import com.smms.assistance.infrastructure.client.BackofficeClient;
import com.smms.assistance.infrastructure.controller.graphql.dto.*;
import com.smms.assistance.infrastructure.repository.HolidayRepository;
import com.smms.assistance.infrastructure.security.AuthContext;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import com.smms.assistance.application.service.UserService;

import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Name;
import org.eclipse.microprofile.graphql.Query;
import org.jboss.logging.Logger;
import org.eclipse.microprofile.jwt.JsonWebToken;
import io.smallrye.graphql.client.typesafe.api.TypesafeGraphQLClientBuilder;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import io.smallrye.common.annotation.Blocking;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@GraphQLApi
@ApplicationScoped
@Blocking
public class ProjectManagerQueryResolver {

    private static final Logger LOG = Logger.getLogger(ProjectManagerQueryResolver.class);

    private static final int ENRICH_POOL_SIZE = 8;
    private final ExecutorService enrichExecutor = Executors.newFixedThreadPool(ENRICH_POOL_SIZE, r -> {
        Thread t = new Thread(r, "enrich-batch");
        t.setDaemon(true);
        return t;
    });

    @jakarta.annotation.PreDestroy
    void shutdownEnrichExecutor() {
        enrichExecutor.shutdown();
    }

    private <T> T inTx(java.util.function.Supplier<T> work) {
        ManagedContext reqCtx = Arc.container().requestContext();
        if (reqCtx.isActive()) {
            return work.get();
        }
        reqCtx.activate();
        try {
            return work.get();
        } finally {
            reqCtx.terminate();
        }
    }

    @Inject AuthContext authContext;

    @Inject AuthClient authClient;

    @Inject JsonWebToken jwt;
    @Inject UserService UserService;

    @Inject GetDashboardPMPort getDashboardPMPort;
    @Inject ListTeamAttendancePort listTeamAttendancePort;
    @Inject ListTeamAbsencesPort listTeamAbsencesPort;
    @Inject ExportAttendancePort exportAttendancePort;
    @Inject ListAttendanceAlertsPort listAttendanceAlertsPort;
    @Inject GetAlertDetailPort getAlertDetailPort;
    @Inject ListJustificationsPMPort listJustificationsPMPort;
    @Inject GetJustificationDetailPMPort getJustificationDetailPMPort;

    @Inject FindProjectByIdUseCase findProjectByIdUseCase;
    @Inject FindAllProjectsUseCase findAllProjectsUseCase;
    @Inject FindProjectMembersUseCase findProjectMembersUseCase;

    @Inject FindAlertsByUserIdUseCase findAlertsByUserIdUseCase;
    @Inject FindAllAlertsUseCase findAllAlertsUseCase;
    @Inject FindNotificationsByTypeUseCase findNotificationsByTypeUseCase;

    @Inject
    com.smms.assistance.infrastructure.storage.S3StorageAdapter s3StorageAdapter;

    @Inject com.smms.assistance.application.in.HasApprovedVacationPort hasApprovedVacationPort;

    @Inject HolidayRepository holidayRepository;
    @Inject com.smms.assistance.infrastructure.repository.CollectionRepository collectionRepository;
    @Inject com.smms.assistance.infrastructure.repository.CollectionItemRepository collectionItemRepository;

    @Inject ScheduleEvaluationService scheduleEvaluationService;

    @Inject com.smms.assistance.application.service.UserBatchCache userBatchCache;

    // =========================
    // HELPER USER FETCH
    // =========================
    private UserOutput fetchUser(UUID userId) {
        return UserService.fetchUser(userId, jwt.getRawToken());
    }

    // =========================
    // LIST USERS (ROLE FILTERED FROM BACKOFFICE)
    // =========================
    @Query("listUsers")
    @RolesAllowed({"ADMIN", "PROJECT_MANAGER"})
    public List<UserOutput> listUsers() {
        authContext.assertAdminOrProjectManager();
        System.out.println("[PM User Enrichment] Fetching users list from Backoffice...");
        try {
            List<UserOutput> allUsers = UserService.listUsers(jwt.getRawToken());
            int usersCount = allUsers != null ? allUsers.size() : 0;
            System.out.println("[PM User Enrichment] Users returned: " + usersCount);
            
            if (allUsers != null) {
                System.out.println("  User IDs requested from Backoffice: " + allUsers.stream().map(u -> u.id).collect(Collectors.toList()));
                for (UserOutput u : allUsers) {
                    System.out.println("  - User: " + u.username 
                            + ", avatarUrl: " + (u.avatarUrl != null && !u.avatarUrl.trim().isEmpty() ? "PRESENT" : "MISSING")
                            + ", Role: " + u.roleName + " (" + u.roleCode + ")");
                }
            }

            // Server-side filtering using dynamic roleCode (avoids hardcoding UUIDs)
            List<UserOutput> filteredUsers = List.of();
            if (allUsers != null) {
                filteredUsers = allUsers.stream()
                        .filter(u -> u.roleCode != null && (u.roleCode.equals("TEAM_MEMBER") || u.roleCode.equals("USER") || u.roleCode.equals("PROJECT_MANAGER")))
                        .collect(Collectors.toList());
            }
            
            System.out.println("[PM User Enrichment] Filtered USERS (TEAM_MEMBER/USER/PROJECT_MANAGER role): " + filteredUsers.size());
            return filteredUsers;
        } catch (Exception e) {
            System.out.println("[PM User Enrichment] Error fetching users from Backoffice: " + e.getMessage());
            throw e;
        }
    }

    // =========================
    // LIST PROJECT MANAGERS (FILTERED TO PROJECT_MANAGER ROLE ONLY)
    // =========================
    @Query("listProjectManagers")
    @RolesAllowed({"ADMIN", "PROJECT_MANAGER"})
    public List<UserOutput> listProjectManagers() {
        authContext.assertAdminOrProjectManager();
        System.out.println("[PM User Enrichment] Fetching project managers list from Backoffice...");
        try {
            List<UserOutput> allUsers = UserService.listUsers(jwt.getRawToken());
            List<UserOutput> filteredUsers = List.of();
            if (allUsers != null) {
                filteredUsers = allUsers.stream()
                        .filter(u -> u.roleCode != null && u.roleCode.equals("PROJECT_MANAGER"))
                        .collect(Collectors.toList());
            }
            
            System.out.println("[PM User Enrichment] Filtered PROJECT_MANAGERS: " + filteredUsers.size());
            return filteredUsers;
        } catch (Exception e) {
            System.out.println("[PM User Enrichment] Error fetching project managers from Backoffice: " + e.getMessage());
            throw e;
        }
    }

    // =========================
    // LIST TEAM MEMBERS (FILTERED TO TEAM_MEMBER/USER ROLE ONLY)
    // =========================
    @Query("listTeamMembers")
    @RolesAllowed({"ADMIN", "PROJECT_MANAGER"})
    public List<UserOutput> listTeamMembers() {
        authContext.assertAdminOrProjectManager();
        System.out.println("[PM User Enrichment] Fetching team members list from Backoffice...");
        try {
            List<UserOutput> allUsers = UserService.listUsers(jwt.getRawToken());
            List<UserOutput> filteredUsers = List.of();
            if (allUsers != null) {
                filteredUsers = allUsers.stream()
                        .filter(u -> u.roleCode != null && (u.roleCode.equals("TEAM_MEMBER") || u.roleCode.equals("USER")))
                        .collect(Collectors.toList());
            }
            
            System.out.println("[PM User Enrichment] Filtered TEAM_MEMBERS: " + filteredUsers.size());
            return filteredUsers;
        } catch (Exception e) {
            System.out.println("[PM User Enrichment] Error fetching team members from Backoffice: " + e.getMessage());
            throw e;
        }
    }

    // =========================
    // DASHBOARD
    // =========================

    @Query("getDashboardPM")
    @RolesAllowed({"ADMIN", "PROJECT_MANAGER"})
    public DashboardOutput getDashboardPM(
            @Name("fromDate") String fromDate,
            @Name("toDate") String toDate,
            @Name("projectId") UUID projectId,
            @Name("userId") UUID userId
    ) {
        authContext.assertAdminOrProjectManager();
        
        if (projectId != null) {
            assertProjectViewPermission(projectId);
        }

        LocalDate from = fromDate != null ? LocalDate.parse(fromDate) : null;
        LocalDate to = toDate != null ? LocalDate.parse(toDate) : null;

        return DtoMapper.toDashboardOutput(
                getDashboardPMPort.execute(projectId, userId, from, to)
        );
    }

    // =========================
    // ATTENDANCE
    // =========================

    @Query("listTeamAttendance")
    @RolesAllowed({"ADMIN", "PROJECT_MANAGER"})
    public AttendancePageOutput listTeamAttendance(
            @Name("page") int page,
            @Name("size") int size,
            @Name("projectId") UUID projectId,
            @Name("userId") UUID userId,
            @Name("fromDate") String fromDate,
            @Name("toDate") String toDate,
            @Name("status") String status
    ) {
        authContext.assertAdminOrProjectManager();
        
        if (projectId != null) {
            assertProjectViewPermission(projectId);
        }
        System.out.println("[PM Attendance] Request received: projectId=" + projectId + ", userId=" + userId + ", page=" + page + ", size=" + size);

        LocalDate from = fromDate != null ? LocalDate.parse(fromDate) : null;
        LocalDate to = toDate != null ? LocalDate.parse(toDate) : null;

        return DtoMapper.toAttendancePage(
                listTeamAttendancePort.execute(
                        page, size, projectId, userId, from, to, status
                )
        );
    }

    @Query("exportAttendance")
    @RolesAllowed({"ADMIN", "PROJECT_MANAGER"})
    public ExportResultOutput exportAttendance(
            @Name("projectId") UUID projectId,
            @Name("userId") UUID userId,
            @Name("fromDate") String fromDate,
            @Name("toDate") String toDate,
            @Name("status") String status
    ) {
        authContext.assertAdminOrProjectManager();
        
        if (projectId != null) {
            assertProjectViewPermission(projectId);
        }

        LocalDate from = fromDate != null ? LocalDate.parse(fromDate) : null;
        LocalDate to = toDate != null ? LocalDate.parse(toDate) : null;

        return exportAttendancePort.execute(
                projectId, userId, from, to, status
        );
    }

    // =========================
    // ABSENCES
    // =========================

    @Query("listTeamAbsences")
    @RolesAllowed({"ADMIN", "PROJECT_MANAGER"})
    public AbsencePageOutput listTeamAbsences(
            @Name("page") int page,
            @Name("size") int size,
            @Name("projectId") UUID projectId,
            @Name("userId") UUID userId,
            @Name("fromDate") String fromDate,
            @Name("toDate") String toDate,
            @Name("type") String type,
            @Name("justified") Boolean justified
    ) {
        authContext.assertAdminOrProjectManager();
        
        if (projectId != null) {
            assertProjectViewPermission(projectId);
        }

        LocalDate from = fromDate != null ? LocalDate.parse(fromDate) : null;
        LocalDate to = toDate != null ? LocalDate.parse(toDate) : null;

        return DtoMapper.toAbsencePage(
                listTeamAbsencesPort.execute(
                        page, size, projectId, userId, from, to, type, justified
                )
        );
    }

    // =========================
    // ALERTS
    // =========================

    @Query("listAttendanceAlerts")
    @RolesAllowed({"ADMIN", "PROJECT_MANAGER"})
    public AlertPageOutput listAttendanceAlerts(
            @Name("page") int page,
            @Name("size") int size,
            @Name("status") String status,
            @Name("type") String type,
            @Name("fromDate") String fromDate,
            @Name("toDate") String toDate
    ) {
        authContext.assertAdminOrProjectManager();

        // ADMIN sees all alerts; PROJECT_MANAGER only alerts of his own projects
        UUID scope = "ADMIN".equals(authContext.getRole()) ? null : authContext.getUserId();

        LocalDate from = fromDate != null ? LocalDate.parse(fromDate) : null;
        LocalDate to = toDate != null ? LocalDate.parse(toDate) : null;

        return DtoMapper.toAlertPage(
                listAttendanceAlertsPort.execute(
                        page, size, status, type, from, to, scope
                )
        );
    }

    @Query("getAlertDetail")
    @RolesAllowed({"ADMIN", "PROJECT_MANAGER"})
    public AlertDetailOutput getAlertDetail(@Name("id") UUID id) {
        authContext.assertAdminOrProjectManager();

        var alert = getAlertDetailPort.execute(id);
        if (alert != null && "PROJECT_MANAGER".equals(authContext.getRole())) {
            // Check if the alert's project is managed by the PM, or the user is managed by the PM
            if (alert.getProjectId() != null) {
                assertProjectViewPermission(alert.getProjectId());
            } else if (alert.getUserId() != null) {
                assertUserManagementPermission(alert.getUserId());
            }
        }

        return DtoMapper.toAlertDetailOutput(alert);
    }

    // =========================
    // JUSTIFICATIONS
    // =========================

    @Query("listJustificationsPM")
    @RolesAllowed({"ADMIN", "PROJECT_MANAGER"})
    public JustificationPageOutput listJustificationsPM(
            @Name("page") int page,
            @Name("size") int size,
            @Name("userId") UUID userId,
            @Name("status") String status,
            @Name("fromDate") String fromDate,
            @Name("toDate") String toDate
    ) {
        authContext.assertAdminOrProjectManager();
        
        if (userId != null) {
            assertUserManagementPermission(userId);
        }

        LocalDate from = fromDate != null ? LocalDate.parse(fromDate) : null;
        LocalDate to = toDate != null ? LocalDate.parse(toDate) : null;

        return DtoMapper.toJustificationPage(
                listJustificationsPMPort.execute(
                        page, size, userId, status, from, to
                )
        );
    }

    @Query("getJustificationDetail")
    @RolesAllowed({"ADMIN", "PROJECT_MANAGER"})
    public JustificationOutput getJustificationDetail(@Name("id") UUID id) {
        authContext.assertAdminOrProjectManager();

        var justification = getJustificationDetailPMPort.execute(id);
        if (justification != null && justification.getUserId() != null) {
            assertUserManagementPermission(justification.getUserId());
        }

        JustificationOutput output = DtoMapper.toJustificationOutput(justification);
        if (output.getDocumentUrl() != null) {
            output.setDocumentUrl(s3StorageAdapter.getPublicUrl(output.getDocumentUrl()));
        }
        return output;
    }

    // =========================
    // PROJECTS
    // =========================

    private void assertProjectViewPermission(UUID projectId) {
        String role = authContext.getRole();
        if ("ADMIN".equals(role)) {
            System.out.println("ðŸ›¡ï¸ [Security] ADMIN bypass granted for projectId: " + projectId);
            return;
        }
        
        if ("PROJECT_MANAGER".equals(role) || "TEAM_MEMBER".equals(role)) {
            long count = ProjectMember.count("projectId = ?1 and userId = ?2", projectId, authContext.getUserId());
            if (count == 0) {
                System.out.println("ðŸš¨ [Security IDOR Blocked] " + role + " " + authContext.getUserId() + " attempted to access unauthorized projectId: " + projectId);
                throw new SecurityException("Access denied: You are not assigned to this project");
            }
            System.out.println("âœ… [Security] " + role + " membership validated for project: " + projectId);
            return;
        }
        
        System.out.println("ðŸš¨ [Security Blocked] Unauthorized role " + role + " attempted to access projectId: " + projectId);
        throw new SecurityException("Access denied: Insufficient permissions");
    }

    private void assertUserManagementPermission(UUID targetUserId) {
        String role = authContext.getRole();
        if ("ADMIN".equals(role)) {
            System.out.println("ðŸ›¡ï¸ [Security] ADMIN bypass granted for user query: " + targetUserId);
            return;
        }
        
        if ("PROJECT_MANAGER".equals(role)) {
            if (targetUserId == null) return;
            long count = ProjectMember.count("projectId in (select m2.projectId from ProjectMember m2 where m2.userId = ?1) and userId = ?2", authContext.getUserId(), targetUserId);
            if (count == 0) {
                System.out.println("ðŸš¨ [Security IDOR Blocked - Cross-Validation] PM " + authContext.getUserId() + " attempted to query unauthorized targetUserId: " + targetUserId);
                throw new SecurityException("Access denied: You do not manage this user");
            }
            System.out.println("âœ… [Security - Cross-Validation] PM ownership verified for targetUserId: " + targetUserId);
            return;
        }
        
        System.out.println("ðŸš¨ [Security Blocked] Unauthorized role " + role + " attempted cross-validation for user: " + targetUserId);
        throw new SecurityException("Access denied: Insufficient permissions");
    }

    @Query("getProjectById")
    @RolesAllowed({"ADMIN", "PROJECT_MANAGER"})
    public ProjectOutput getProjectById(@Name("id") UUID id) {
        assertProjectViewPermission(id);
        Project p = findProjectByIdUseCase.execute(id);
        if (p == null) return null;
        return enrichProjectsBatch(List.of(p), authContext.getUserId()).stream().findFirst().orElse(null);
    }

    @Query("getAllProjects")
    @RolesAllowed({"ADMIN", "PROJECT_MANAGER"})
    public List<ProjectOutput> getAllProjects() {
        String role = authContext.getRole();
        if ("ADMIN".equals(role)) {
            List<Project> all = findAllProjectsUseCase.execute();
            return enrichProjectsBatch(all, authContext.getUserId());
        }

        // Project Manager sees only assigned projects
        UUID userId = authContext.getUserId();
        List<ProjectMember> memberships = ProjectMember.list("userId = ?1", userId);
        java.util.Set<UUID> projectIds = memberships.stream()
                .map(m -> m.projectId)
                .collect(Collectors.toSet());

        List<Project> projectsByMember = projectIds.isEmpty() ? List.of() : Project.list("id in ?1 and deletedAt is null", projectIds);
        List<Project> projectsByResponsible = Project.list("responsibleId = ?1 and deletedAt is null", userId);

        java.util.Map<UUID, Project> projectMap = new java.util.HashMap<>();
        for (Project p : projectsByMember) projectMap.put(p.id, p);
        for (Project p : projectsByResponsible) projectMap.put(p.id, p);

        List<Project> projects = new java.util.ArrayList<>(projectMap.values());
        if (projects.isEmpty()) return List.of();

        return enrichProjectsBatch(projects, userId);
    }

    @Query("getMyProjects")
    @RolesAllowed({"ADMIN", "PROJECT_MANAGER", "TEAM_MEMBER"})
    public List<ProjectOutput> getMyProjects() {
        UUID userId = authContext.getUserId();
        String role = authContext.getRole();
        System.out.println("ðŸ“¡ [PM Backend] GetMyProjects called by user: " + userId + " with role: " + role);

        List<ProjectMember> memberships = ProjectMember.list("userId = ?1", userId);
        java.util.Set<UUID> projectIds = memberships.stream()
                .map(m -> m.projectId)
                .collect(Collectors.toSet());

        List<Project> projectsByMember = projectIds.isEmpty() ? List.of() : Project.list("id in ?1 and deletedAt is null", projectIds);
        List<Project> projectsByResponsible = Project.list("responsibleId = ?1 and deletedAt is null", userId);

        java.util.Map<UUID, Project> projectMap = new java.util.HashMap<>();
        for (Project p : projectsByMember) projectMap.put(p.id, p);
        for (Project p : projectsByResponsible) projectMap.put(p.id, p);

        List<Project> projects = new java.util.ArrayList<>(projectMap.values());

        if ("TEAM_MEMBER".equals(role)) {
            System.out.println("ðŸ“ [Audit Log] Team Member retrieved project list. userId: " + userId + ", projects count: " + projects.size());
        }

        return enrichProjectsBatch(projects, userId);
    }

    private List<ProjectOutput> enrichProjectsBatch(List<Project> projects, UUID contextUserId) {
        if (projects == null || projects.isEmpty()) return List.of();
        long start = System.currentTimeMillis();
        
        List<UUID> projectIds = projects.stream().map(p -> p.id).collect(Collectors.toList());
        
        // === Wave A: cargas independientes en paralelo ===
        CompletableFuture<List<ShiftTemplateEntity>> templatesF = CompletableFuture.supplyAsync(
                () -> inTx(() -> ShiftTemplateEntity.find("projectId in ?1 order by createdAt asc", projectIds).list()),
                enrichExecutor);

        CompletableFuture<List<ScheduleEntity>> overridesF = contextUserId == null
                ? CompletableFuture.completedFuture(List.of())
                : CompletableFuture.supplyAsync(
                        () -> inTx(() -> ScheduleEntity.find("userId = ?1 and projectId in ?2", contextUserId, projectIds).list()),
                        enrichExecutor);

        CompletableFuture<List<CollectionEntity>> globalColsF = CompletableFuture.supplyAsync(
                () -> inTx(() -> CollectionEntity.list("scope", "GLOBAL")),
                enrichExecutor);

        CompletableFuture<List<ProjectCollectionEntity>> projColsF = CompletableFuture.supplyAsync(
                () -> inTx(() -> ProjectCollectionEntity.list("projectId in ?1", projectIds)),
                enrichExecutor);

        CompletableFuture<List<HolidayEntity>> userIncludeF = contextUserId == null
                ? CompletableFuture.completedFuture(List.of())
                : CompletableFuture.supplyAsync(
                        () -> inTx(() -> HolidayEntity.find("type = ?1 and targetId = ?2", "USER_INCLUDE", contextUserId).list()),
                        enrichExecutor);

        CompletableFuture<List<HolidayEntity>> userExcludeF = contextUserId == null
                ? CompletableFuture.completedFuture(List.of())
                : CompletableFuture.supplyAsync(
                        () -> inTx(() -> HolidayEntity.find("type = ?1 and targetId = ?2", "USER_EXCLUDE", contextUserId).list()),
                        enrichExecutor);

        CompletableFuture<List<ProjectMember>> membersF = CompletableFuture.supplyAsync(
                () -> inTx(() -> ProjectMember.list("projectId in ?1", projectIds)),
                enrichExecutor);

        CompletableFuture<List<ScheduleEntity>> memberSchedulesF = CompletableFuture.supplyAsync(
                () -> inTx(() -> ScheduleEntity.find("projectId in ?1", projectIds).list()),
                enrichExecutor);

        // --- Join wave A y armado de mapas ---
        java.util.Map<UUID, ShiftTemplateEntity> templateMap = new java.util.HashMap<>();
        for (ShiftTemplateEntity t : templatesF.join()) {
            templateMap.put(t.getProjectId(), t); // Latest overwrites earlier ones
        }

        java.util.Map<UUID, ScheduleEntity> scheduleMap = new java.util.HashMap<>();
        for (ScheduleEntity s : overridesF.join()) {
            scheduleMap.put(s.getProjectId(), s);
        }

        // 3. Collections
        List<CollectionEntity> globalCols = globalColsF.join();
        List<UUID> activeCollectionIds = new java.util.ArrayList<>();
        for (CollectionEntity c : globalCols) activeCollectionIds.add(c.getId());

        java.util.Map<UUID, List<UUID>> projectToCollectionIds = new java.util.HashMap<>();
        for (ProjectCollectionEntity pc : projColsF.join()) {
            projectToCollectionIds.computeIfAbsent(pc.getProjectId(), k -> new java.util.ArrayList<>()).add(pc.getCollectionId());
            activeCollectionIds.add(pc.getCollectionId());
        }

        // 4. User exceptions
        List<String> userIncludes = new java.util.ArrayList<>();
        List<String> userExcludes = new java.util.ArrayList<>();
        for (HolidayEntity h : userIncludeF.join()) userIncludes.add(h.getDate().toString());
        for (HolidayEntity h : userExcludeF.join()) userExcludes.add(h.getDate().toString());

        // 5. Members
        List<ProjectMember> allMembers = membersF.join();
        java.util.Map<UUID, List<ProjectMember>> membersByProject = new java.util.HashMap<>();
        for (ProjectMember m : allMembers) {
            membersByProject.computeIfAbsent(m.projectId, k -> new java.util.ArrayList<>()).add(m);
        }

        // === Wave B: dependen de resultados de la wave A ===

        // Items de colecciones (necesita activeCollectionIds)
        CompletableFuture<List<CollectionItemEntity>> itemsF = activeCollectionIds.isEmpty()
                ? CompletableFuture.completedFuture(List.of())
                : CompletableFuture.supplyAsync(
                        () -> inTx(() -> CollectionItemEntity.list("collectionId in ?1", activeCollectionIds)),
                        enrichExecutor);

        // Usuarios del equipo en batch (necesita los userIds de los miembros)
        java.util.Set<UUID> teamUserIds = allMembers.stream().map(m -> m.userId).collect(Collectors.toSet());
        if (contextUserId != null) teamUserIds.add(contextUserId);
        String tempToken = null;
        try {
            if (jwt != null && jwt.getRawToken() != null) {
                tempToken = jwt.getRawToken();
            }
        } catch (Exception ignored) {}
        final String capturedToken = tempToken;
        CompletableFuture<List<UserOutput>> usersF = teamUserIds.isEmpty()
                ? CompletableFuture.completedFuture(List.of())
                : CompletableFuture.supplyAsync(() -> UserService.fetchUsersBatch(new java.util.ArrayList<>(teamUserIds), capturedToken), enrichExecutor);

        // Join wave B
        java.util.Map<UUID, List<String>> collectionDates = new java.util.HashMap<>();
        for (CollectionItemEntity item : itemsF.join()) {
            collectionDates.computeIfAbsent(item.getCollectionId(), k -> new java.util.ArrayList<>()).add(item.getDate().toString());
        }

        // 5b. Pre-cargar en batch los usuarios del equipo (1 viaje cacheado) para el resolver user(@Source)
        for (UserOutput u : usersF.join()) {
            if (u.id != null) userBatchCache.put(u.id, u);
        }

        java.util.Map<UUID, java.util.Set<UUID>> usersWithScheduleByProject = new java.util.HashMap<>();
        for (ScheduleEntity s : memberSchedulesF.join()) {
            usersWithScheduleByProject.computeIfAbsent(s.getProjectId(), k -> new java.util.HashSet<>()).add(s.getUserId());
        }

        // --- Assemble results ---
        List<ProjectOutput> results = new java.util.ArrayList<>();
        for (Project project : projects) {
            ProjectOutput output = new ProjectOutput();
            output.id = project.id;
            output.name = project.name;
            output.description = project.description;
            output.status = project.status;
            output.startDate = project.startDate;
            output.endDate = project.endDate;
            output.budget = project.budget;
            output.currency = project.currency;
            output.createdAt = project.createdAt;
            output.latitude = project.latitude;
            output.longitude = project.longitude;
            output.radius = project.radius;
            output.workStartTime = project.workStartTime != null ? project.workStartTime.toString() : null;
            output.workEndTime = project.workEndTime != null ? project.workEndTime.toString() : null;
            output.graceMinutes = project.graceMinutes;
            output.absenceCutoffTime = project.absenceCutoffTime != null ? project.absenceCutoffTime.toString() : null;
            output.shiftType = null;
            output.responsibleId = project.responsibleId;
            output.timezone = project.timezone;
            output.vacationEligibilityDays = project.vacationEligibilityDays;
            
            ShiftTemplateEntity template = templateMap.get(project.id);
            if (template != null) {
                output.shiftType = template.getShiftType();
                output.graceMinutes = template.getGraceMinutes();
                output.absenceCutoffTime = template.getAbsenceCutoffTime() != null ? template.getAbsenceCutoffTime().toString() : null;
                if (template.getShiftType().startsWith("ROTATING")) {
                    output.workStartTime = template.getRotationShiftStartTime() != null ? template.getRotationShiftStartTime().toString() : null;
                    output.workEndTime = template.getRotationShiftEndTime() != null ? template.getRotationShiftEndTime().toString() : null;
                } else if (!"FLEXIBLE".equals(template.getShiftType())) {
                    output.workStartTime = template.getWorkStartTime() != null ? template.getWorkStartTime().toString() : null;
                    output.workEndTime = template.getWorkEndTime() != null ? template.getWorkEndTime().toString() : null;
                }
            }

            if (contextUserId != null) {
                ScheduleEntity override = scheduleMap.get(project.id);
                if (override != null) {
                    if (override.getWorkStartTime() != null) output.workStartTime = override.getWorkStartTime().toString();
                    if (override.getWorkEndTime() != null) output.workEndTime = override.getWorkEndTime().toString();
                    if (override.getGraceMinutes() != null) output.graceMinutes = override.getGraceMinutes();
                }
            }

            java.util.Set<String> computedHolidays = new java.util.HashSet<>();
            for (CollectionEntity c : globalCols) {
                List<String> dates = collectionDates.get(c.getId());
                if (dates != null) computedHolidays.addAll(dates);
            }
            List<UUID> pCols = projectToCollectionIds.get(project.id);
            if (pCols != null) {
                for (UUID pcId : pCols) {
                    List<String> dates = collectionDates.get(pcId);
                    if (dates != null) computedHolidays.addAll(dates);
                }
            }
            computedHolidays.addAll(userIncludes);
            computedHolidays.removeAll(userExcludes);
            output.holidays = new java.util.ArrayList<>(computedHolidays);

            List<ProjectMember> pmems = membersByProject.get(project.id);
            List<ProjectMemberOutput> memberOutputs = new java.util.ArrayList<>();
            if (pmems != null) {
                java.util.Set<UUID> usersWithSched = usersWithScheduleByProject.get(project.id);
                if (usersWithSched == null) usersWithSched = java.util.Collections.emptySet();
                for (ProjectMember pm : pmems) {
                    ProjectMemberOutput mo = toProjectMemberOutput(pm);
                    mo.hasCustomSchedule = usersWithSched.contains(pm.userId);
                    memberOutputs.add(mo);
                }
            }
            output.members = memberOutputs;
            results.add(output);
        }

        long duration = System.currentTimeMillis() - start;
        System.out.println("[PERF] enrichProjectsBatch (projects=" + projects.size() + ") -> Native Batch Duration: " + duration + " ms");
        return results;
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

    // =========================
    // SCHEDULE OVERRIDES
    // =========================

    @Query("listScheduleOverrides")
    @RolesAllowed({"ADMIN", "PROJECT_MANAGER"})
    public List<ScheduleOutput> listScheduleOverrides(
            @Name("projectId") UUID projectId,
            @Name("userId") UUID userId,
            @Name("page") @org.eclipse.microprofile.graphql.DefaultValue("1") int page,
            @Name("size") @org.eclipse.microprofile.graphql.DefaultValue("100") int size
    ) {
        authContext.assertAdminOrProjectManager();
        
        if (projectId != null) {
            assertProjectViewPermission(projectId);
        }

        StringBuilder query = new StringBuilder("1 = 1");
        java.util.Map<String, Object> params = new java.util.HashMap<>();

        if (projectId != null) {
            query.append(" and projectId = :projectId");
            params.put("projectId", projectId);
        }
        if (userId != null) {
            query.append(" and userId = :userId");
            params.put("userId", userId);
        }

        int internalPage = Math.max(page - 1, 0);
        int internalSize = Math.max(size, 1);

        return ScheduleEntity.find(query.toString(), params)
                .page(internalPage, internalSize)
                .<ScheduleEntity>list()
                .stream()
                .map(e -> ScheduleOutput.builder()
                        .id(e.getId())
                        .userId(e.getUserId())
                        .projectId(e.getProjectId())
                        .workStartTime(e.getWorkStartTime() != null ? e.getWorkStartTime().toString() : null)
                        .workEndTime(e.getWorkEndTime() != null ? e.getWorkEndTime().toString() : null)
                        .graceMinutes(e.getGraceMinutes())
                        .shiftType(e.getShiftType())
                        .mondayStart(e.getMondayStart() != null ? e.getMondayStart().toString() : null)
                        .mondayEnd(e.getMondayEnd() != null ? e.getMondayEnd().toString() : null)
                        .tuesdayStart(e.getTuesdayStart() != null ? e.getTuesdayStart().toString() : null)
                        .tuesdayEnd(e.getTuesdayEnd() != null ? e.getTuesdayEnd().toString() : null)
                        .wednesdayStart(e.getWednesdayStart() != null ? e.getWednesdayStart().toString() : null)
                        .wednesdayEnd(e.getWednesdayEnd() != null ? e.getWednesdayEnd().toString() : null)
                        .thursdayStart(e.getThursdayStart() != null ? e.getThursdayStart().toString() : null)
                        .thursdayEnd(e.getThursdayEnd() != null ? e.getThursdayEnd().toString() : null)
                        .fridayStart(e.getFridayStart() != null ? e.getFridayStart().toString() : null)
                        .fridayEnd(e.getFridayEnd() != null ? e.getFridayEnd().toString() : null)
                        .saturdayStart(e.getSaturdayStart() != null ? e.getSaturdayStart().toString() : null)
                        .saturdayEnd(e.getSaturdayEnd() != null ? e.getSaturdayEnd().toString() : null)
                        .sundayStart(e.getSundayStart() != null ? e.getSundayStart().toString() : null)
                        .sundayEnd(e.getSundayEnd() != null ? e.getSundayEnd().toString() : null)
                        .rotationWorkDays(e.getRotationWorkDays())
                        .rotationRestDays(e.getRotationRestDays())
                        .rotationShiftStartTime(e.getRotationShiftStartTime() != null ? e.getRotationShiftStartTime().toString() : null)
                        .rotationShiftEndTime(e.getRotationShiftEndTime() != null ? e.getRotationShiftEndTime().toString() : null)
                        .validFrom(e.getValidFrom() != null ? e.getValidFrom().toString() : null)
                        .validUntil(e.getValidUntil() != null ? e.getValidUntil().toString() : null)
                        .absenceCutoffTime(e.getAbsenceCutoffTime() != null ? e.getAbsenceCutoffTime().toString() : null)
                        .timezone(e.getTimezone())
                        .build())
                .collect(Collectors.toList());
    }

    @Query("getEffectiveSchedule")
    @RolesAllowed({"ADMIN", "PROJECT_MANAGER", "TEAM_MEMBER"})
    public EffectiveScheduleOutput getEffectiveSchedule(
            @Name("userId") UUID userId,
            @Name("projectId") UUID projectId,
            @Name("date") String dateString
    ) {
        if (projectId != null) {
            assertProjectViewPermission(projectId);
        }
        
        LocalDate date = dateString != null ? LocalDate.parse(dateString) : LocalDate.now();
        return scheduleEvaluationService.getEffectiveSchedule(userId, projectId, date);
    }

    private List<ProjectMemberOutput> getProjectMembersInternal(UUID projectId) {
        java.util.List<com.smms.assistance.domain.entity.ScheduleEntity> schedules = com.smms.assistance.domain.entity.ScheduleEntity.find("projectId", projectId).list();
        java.util.Set<UUID> usersWithSchedule = schedules.stream().map(com.smms.assistance.domain.entity.ScheduleEntity::getUserId).collect(Collectors.toSet());

        return findProjectMembersUseCase.execute(projectId)
                .stream()
                .map(member -> {
                    ProjectMemberOutput output = toProjectMemberOutput(member);
                    output.hasCustomSchedule = usersWithSchedule.contains(member.userId);
                    return output;
                })
                .collect(Collectors.toList());
    }

    @Query("getProjectMembers")
    @RolesAllowed({"ADMIN", "PROJECT_MANAGER"})
    public List<ProjectMemberOutput> getProjectMembers(@Name("projectId") UUID projectId) {
        assertProjectViewPermission(projectId);
        return getProjectMembersInternal(projectId);
    }

    @Name("user")
    public UserOutput user(@org.eclipse.microprofile.graphql.Source ProjectMemberOutput member) {
        if (member == null || member.userId == null) return null;
        UserOutput cached = userBatchCache.get(member.userId);
        if (cached != null) return cached;
        return fetchUser(member.userId);
    }

    // =========================
    // ALERTS BY USER
    // =========================

    @Query("getAlertsByUser")
    @RolesAllowed({"ADMIN", "PROJECT_MANAGER"})
    public List<NotificationOutput> getAlertsByUser(@Name("userId") UUID userId) {

        authContext.assertAdminOrProjectManager();
        
        if (userId != null) {
            assertUserManagementPermission(userId);
        }

        return findAlertsByUserIdUseCase.execute(userId)
                .stream()
                .map(this::toNotificationOutput)
                .collect(Collectors.toList());
    }

    @Query("getAllAlerts")
    @RolesAllowed({"ADMIN", "PROJECT_MANAGER"})
    public List<NotificationOutput> getAllAlerts() {

        authContext.assertAdminOrProjectManager();
        if ("PROJECT_MANAGER".equals(authContext.getRole())) {
             throw new SecurityException("Access denied: Project Managers cannot query all global alerts.");
        }

        return findAllAlertsUseCase.execute()
                .stream()
                .map(this::toNotificationOutput)
                .collect(Collectors.toList());
    }

    @Query("getNotificationsByType")
    @RolesAllowed({"ADMIN", "PROJECT_MANAGER"})
    public List<NotificationOutput> getNotificationsByType(
            @Name("userId") UUID userId,
            @Name("type") String type
    ) {

        authContext.assertAdminOrProjectManager();
        
        if (userId != null) {
            assertUserManagementPermission(userId);
        }

        return findNotificationsByTypeUseCase.execute(userId, type)
                .stream()
                .map(this::toNotificationOutput)
                .collect(Collectors.toList());
    }

    // =========================
    // SHIFT TEMPLATES
    // =========================

    @Query("listShiftTemplates")
    @RolesAllowed({"ADMIN", "PROJECT_MANAGER"})
    public List<ShiftTemplateOutput> listShiftTemplates(@Name("projectId") UUID projectId) {
        StringBuilder query = new StringBuilder("1 = 1");
        java.util.Map<String, Object> params = new java.util.HashMap<>();

        if (projectId != null) {
            assertProjectViewPermission(projectId);
            query.append(" and projectId = :projectId");
            params.put("projectId", projectId);
        } else {
            String role = authContext.getRole();
            if (!"ADMIN".equals(role)) {
                UUID userId = authContext.getUserId();
                List<ProjectMember> memberships = ProjectMember.list("userId = ?1", userId);
                List<UUID> allowedProjectIds = memberships.stream().map(m -> m.projectId).collect(Collectors.toList());
                if (allowedProjectIds.isEmpty()) {
                    return java.util.Collections.emptyList();
                }
                query.append(" and projectId in (:projectIds)");
                params.put("projectIds", allowedProjectIds);
            }
        }

        return ShiftTemplateEntity.find(query.toString(), params)
                .<ShiftTemplateEntity>list()
                .stream()
                .map(this::toShiftTemplateOutput)
                .collect(Collectors.toList());
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

    private UUID parseUuid(String s) {
        if (s == null || s.isEmpty()) return null;
        try { return UUID.fromString(s); } catch (IllegalArgumentException e) { return null; }
    }

    @Query("listVacations")
    @RolesAllowed({"TEAM_MEMBER", "PROJECT_MANAGER", "ADMIN"})
    @Transactional
    public List<VacationRequestOutput> listVacations(
            @Name("userId") @org.eclipse.microprofile.graphql.DefaultValue("null") String userId,
            @Name("status") @org.eclipse.microprofile.graphql.DefaultValue("null") String status,
            @Name("page") @org.eclipse.microprofile.graphql.DefaultValue("1") int page,
            @Name("size") @org.eclipse.microprofile.graphql.DefaultValue("100") int size
    ) {
        UUID uid = parseUuid(userId);
        var jpql = "select v from VacationRequestEntity v where 1=1";
        var params = new java.util.ArrayList<>();

        if (uid != null) {
            jpql += " and v.userId = ?" + (params.size() + 1);
            params.add(uid);
        }
        if (status != null && !status.isEmpty() && !"null".equalsIgnoreCase(status) && !"ALL".equalsIgnoreCase(status)) {
            jpql += " and v.status = ?" + (params.size() + 1);
            params.add(status);
        }

        jpql += " order by v.createdAt desc";

        var q = com.smms.assistance.domain.entity.VacationRequestEntity.getEntityManager()
                .createQuery(jpql, com.smms.assistance.domain.entity.VacationRequestEntity.class);
        for (int i = 0; i < params.size(); i++) {
            q.setParameter(i + 1, params.get(i));
        }
        q.setFirstResult(Math.max(page - 1, 0) * size);
        q.setMaxResults(Math.max(size, 1));
        var result = q.getResultList().stream()
                .map(this::toVacationOutput)
                .collect(Collectors.toList());
        LOG.infof("[VACATIONS] ListVacations: userId=%s, status=%s, count=%d", userId, status, result.size());
        return result;
    }

    @Query("getVacationBalance")
    @RolesAllowed({"TEAM_MEMBER", "PROJECT_MANAGER", "ADMIN"})
    @Transactional
    public VacationBalanceOutput getVacationBalance(@Name("userId") String userId) {
        UUID uid = parseUuid(userId);
        if (uid == null) {
            return VacationBalanceOutput.builder()
                    .totalDays(30).usedDays(0).pendingDays(0).availableDays(30)
                    .year(java.time.LocalDate.now().getYear()).build();
        }
        int year = java.time.LocalDate.now().getYear();
        var opt = VacationBalanceEntity
                .find("userId = ?1 and year = ?2", uid, year)
                .firstResultOptional();

        if (opt.isEmpty()) {
            return VacationBalanceOutput.builder()
                    .userId(uid)
                    .totalDays(30)
                    .usedDays(0)
                    .pendingDays(0)
                    .availableDays(30)
                    .year(year)
                    .build();
        }

        var bal = (com.smms.assistance.domain.entity.VacationBalanceEntity) opt.get();
        return VacationBalanceOutput.builder()
                .id(bal.getId())
                .userId(bal.getUserId())
                .totalDays(bal.getTotalDays())
                .usedDays(bal.getUsedDays())
                .pendingDays(bal.getPendingDays())
                .availableDays(bal.getTotalDays() - bal.getUsedDays() - bal.getPendingDays())
                .year(bal.getYear())
                .build();
    }

    @Query("hasApprovedVacation")
    @RolesAllowed({"TEAM_MEMBER", "PROJECT_MANAGER", "ADMIN"})
    public boolean hasApprovedVacation(@Name("userId") UUID userId, @Name("date") java.time.LocalDate date) {
        return hasApprovedVacationPort.execute(userId, date).isPresent();
    }

    @Query("getMyVacationEligibility")
    @RolesAllowed({"TEAM_MEMBER", "PROJECT_MANAGER", "ADMIN"})
    public VacationEligibilityOutput getMyVacationEligibility(@Name("projectId") UUID projectId) {
        UUID userId = authContext.getUserId();

        ProjectMember pm = ProjectMember.find("projectId = ?1 and userId = ?2", projectId, userId).firstResult();
        if (pm == null || pm.createdAt == null) {
            return new VacationEligibilityOutput(90, -1, false, null);
        }

        Project project = Project.findById(projectId);
        int requiredDays = (project != null && project.vacationEligibilityDays != null)
                ? project.vacationEligibilityDays
                : 90;

        long daysElapsed = java.time.temporal.ChronoUnit.DAYS.between(
                pm.createdAt.toLocalDate(),
                LocalDate.now()
        );
        boolean isEligible = daysElapsed >= requiredDays;

        return new VacationEligibilityOutput(requiredDays, daysElapsed, isEligible, pm.createdAt.toString());
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

    @Query("listholidays")
    @RolesAllowed({"ADMIN", "PROJECT_MANAGER"})
    public java.util.List<HolidayOutput> listHolidays(@Name("targetId") UUID targetId, @Name("year") int year) {
        java.time.LocalDate from = java.time.LocalDate.of(year, 1, 1);
        java.time.LocalDate to = java.time.LocalDate.of(year, 12, 31);
        java.util.List<HolidayOutput> projectHolidays = holidayRepository.findByTargetIdAndDateRange(targetId, from, to).stream()
                .map(h -> HolidayOutput.builder()
                        .id(h.getId())
                        .type(h.getType())
                        .targetId(h.getTargetId())
                        .date(h.getDate())
                        .name(h.getName())
                        .build())
                .collect(java.util.stream.Collectors.toList());
        java.util.List<HolidayOutput> globalHolidays = holidayRepository.findGlobalHolidays().stream()
                .filter(h -> !h.getDate().isBefore(from) && !h.getDate().isAfter(to))
                .map(h -> HolidayOutput.builder()
                        .id(h.getId())
                        .type(h.getType())
                        .targetId(h.getTargetId())
                        .date(h.getDate())
                        .name(h.getName())
                        .build())
                .collect(java.util.stream.Collectors.toList());
        java.util.List<HolidayOutput> combined = new java.util.ArrayList<>(projectHolidays);
        for (HolidayOutput gh : globalHolidays) {
            if (projectHolidays.stream().noneMatch(ph -> ph.getDate().equals(gh.getDate()) && gh.getType().equals("GLOBAL"))) {
                combined.add(gh);
            }
        }
        return combined.stream().sorted(java.util.Comparator.comparing(HolidayOutput::getDate)).collect(java.util.stream.Collectors.toList());
    }

    @Query("isHolidayForUser")
    @RolesAllowed({"TEAM_MEMBER", "PROJECT_MANAGER", "ADMIN"})
    public boolean isHolidayForUser(@Name("projectId") UUID projectId, @Name("userId") UUID userId, @Name("date") java.time.LocalDate date) {
        return holidayRepository.isHolidayForUser(projectId, userId, date);
    }

    @Query("isHoliday")
@RolesAllowed({"TEAM_MEMBER", "PROJECT_MANAGER", "ADMIN"})
    public boolean isHoliday(@Name("projectId") UUID projectId, @Name("date") java.time.LocalDate date) {
        if (projectId == null || date == null) return false;
        return holidayRepository.isHolidayForUser(projectId, null, date);
    }

    @Query("getAdminSetting")
    @RolesAllowed({"ADMIN", "PROJECT_MANAGER"})
    public String getAdminSetting(@Name("key") String key) {
        var em = com.smms.assistance.domain.entity.HolidayEntity.getEntityManager();
        Object result = em.createNativeQuery("select value from admin_settings where key = ?1")
                .setParameter(1, key)
                .getResultStream()
                .findFirst()
                .orElse(null);
        return result != null ? result.toString() : null;
    }

    // =========================
    // COLLECTIONS (HOLIDAYS)
    // =========================

    @Query("getGlobalCollections")
    @RolesAllowed({"ADMIN", "PROJECT_MANAGER"})
    public List<CollectionOutput> getGlobalCollections() {
        authContext.assertAdminOrProjectManager();
        List<CollectionEntity> collections = collectionRepository.findGlobalCollections();
        if (collections.isEmpty()) return List.of();

        List<UUID> collectionIds = collections.stream().map(CollectionEntity::getId).collect(Collectors.toList());
        List<com.smms.assistance.domain.entity.CollectionItemEntity> allItems = collectionItemRepository.findByCollectionIds(collectionIds);
        java.util.Map<UUID, List<com.smms.assistance.domain.entity.CollectionItemEntity>> itemsByCollection = allItems.stream()
                .collect(Collectors.groupingBy(com.smms.assistance.domain.entity.CollectionItemEntity::getCollectionId));

        return collections.stream().map(c -> {
            List<com.smms.assistance.domain.entity.CollectionItemEntity> items = itemsByCollection.getOrDefault(c.getId(), List.of());
            return CollectionOutput.builder()
                    .id(c.getId())
                    .name(c.getName())
                    .scope(c.getScope())
                    .ownerId(c.getOwnerId())
                    .createdAt(c.getCreatedAt())
                    .items(items.stream().map(i -> CollectionItemOutput.builder()
                            .id(i.getId())
                            .collectionId(i.getCollectionId())
                            .date(i.getDate())
                            .name(i.getName())
                            .build()).collect(Collectors.toList()))
                    .build();
        }).collect(Collectors.toList());
    }

    @Query("getCollectionsForProject")
    @RolesAllowed({"ADMIN", "PROJECT_MANAGER"})
    public List<CollectionOutput> getCollectionsForProject(@Name("projectId") UUID projectId) {
        authContext.assertAdminOrProjectManager();
        
        java.util.List<CollectionEntity> combined = collectionRepository.findCollectionsForProjectUnified(projectId);
        if (combined.isEmpty()) return List.of();

        List<UUID> collectionIds = combined.stream().map(CollectionEntity::getId).collect(Collectors.toList());
        List<com.smms.assistance.domain.entity.CollectionItemEntity> allItems = collectionItemRepository.findByCollectionIds(collectionIds);
        java.util.Map<UUID, List<com.smms.assistance.domain.entity.CollectionItemEntity>> itemsByCollection = allItems.stream()
                .collect(Collectors.groupingBy(com.smms.assistance.domain.entity.CollectionItemEntity::getCollectionId));

        return combined.stream().map(c -> {
            List<com.smms.assistance.domain.entity.CollectionItemEntity> items = itemsByCollection.getOrDefault(c.getId(), List.of());
            return CollectionOutput.builder()
                    .id(c.getId())
                    .name(c.getName())
                    .scope(c.getScope())
                    .ownerId(c.getOwnerId())
                    .createdAt(c.getCreatedAt())
                    .items(items.stream().map(i -> CollectionItemOutput.builder()
                            .id(i.getId())
                            .collectionId(i.getCollectionId())
                            .date(i.getDate())
                            .name(i.getName())
                            .build()).collect(Collectors.toList()))
                    .build();
        }).collect(Collectors.toList());
    }

    // User exceptions are still handled by HolidayEntity
    @Query("getHolidayExceptionsForUser")
    @RolesAllowed({"ADMIN", "PROJECT_MANAGER"})
    public List<HolidayOutput> getHolidayExceptionsForUser(@Name("userId") UUID userId) {
        authContext.assertAdminOrProjectManager();
        return holidayRepository.findByTargetId(userId).stream().map(h -> HolidayOutput.builder()
                .id(h.getId())
                .type(h.getType())
                .targetId(h.getTargetId())
                .date(h.getDate())
                .name(h.getName())
                .build()).collect(Collectors.toList());
    }
}
