package com.smms.assistance.application.usecase;

import com.smms.assistance.application.in.ProjectPort;
import com.smms.assistance.domain.entity.Project;
import com.smms.assistance.domain.entity.ProjectMember;
import com.smms.assistance.infrastructure.controller.graphql.dto.ProjectInput;
import com.smms.assistance.infrastructure.controller.graphql.dto.ProjectMemberInput;
import com.smms.assistance.infrastructure.messaging.OutboxEventPublisher;
import com.smms.assistance.infrastructure.repository.HolidayRepository;
import com.smms.assistance.domain.entity.HolidayEntity;
import java.time.LocalDate;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

import com.smms.assistance.shared.util.TimezoneService;

@ApplicationScoped
public class CreateProjectUseCase {

    private static final Logger log = Logger.getLogger(CreateProjectUseCase.class);

    private final ProjectPort projectPort;
    private final OutboxEventPublisher outboxPublisher;
    private final HolidayRepository holidayRepository;

    @Inject
    public CreateProjectUseCase(ProjectPort projectPort, OutboxEventPublisher outboxPublisher, HolidayRepository holidayRepository) {
        this.projectPort = projectPort;
        this.outboxPublisher = outboxPublisher;
        this.holidayRepository = holidayRepository;
    }

    @Transactional
    public Project execute(ProjectInput input) {

        log.info("========== CREATE PROJECT INPUT ==========");
        log.info("name: " + input.name);
        log.info("description: " + input.description);
        log.info("status: " + input.status);
        log.info("startDate: " + input.startDate);
        log.info("endDate: " + input.endDate);
        log.info("budget: " + input.budget);
        log.info("currency: " + input.currency);

        log.info("RAW workStartTime: " + input.workStartTime);
        log.info("RAW workEndTime: " + input.workEndTime);
        log.info("RAW graceMinutes: " + input.graceMinutes);

        // Anti-spam / Double-submission guard (debounce identical creates within 15 seconds)
        if (input.name != null && !input.name.trim().isEmpty()) {
            String trimmedName = input.name.trim();
            java.util.List<Project> recentProjects = Project.find("lower(trim(name)) = lower(?1) order by createdAt desc", trimmedName).list();
            if (!recentProjects.isEmpty()) {
                Project latest = recentProjects.get(0);
                if (latest.createdAt != null) {
                    long diffSeconds = java.time.Duration.between(latest.createdAt, TimezoneService.utcNow()).abs().getSeconds();
                    if (diffSeconds < 15) {
                        log.warn("⚠️ Double-submission detected for project '" + trimmedName + "' (" + diffSeconds + "s ago). Returning existing project " + latest.id);
                        return latest;
                    }
                }
            }
        }

        Project project = new Project();

        project.id = UUID.randomUUID();
        project.name = input.name;
        project.description = input.description;
        project.status = (input.status != null && !input.status.trim().isEmpty()) ? input.status : "ACTIVE";
        project.startDate = input.startDate;
        project.endDate = input.endDate;
        project.budget = input.budget;
        project.currency = input.currency;

        project.workStartTime = input.workStartTime;
        project.workEndTime = input.workEndTime;
        project.graceMinutes = input.graceMinutes;
        project.absenceCutoffTime = input.absenceCutoffTime;
        project.timezone = (input.timezone != null && TimezoneService.isValidTimezone(input.timezone))
                ? input.timezone
                : TimezoneService.defaultTimezone();

        project.vacationEligibilityDays = input.vacationEligibilityDays != null ? input.vacationEligibilityDays : 90;

        project.responsibleId = input.responsibleId;

        project.createdAt = TimezoneService.utcNow();
        project.updatedAt = TimezoneService.utcNow();

        log.info("========== BEFORE SAVE ==========");
        log.info("workStartTime: " + project.workStartTime);
        log.info("workEndTime: " + project.workEndTime);
        log.info("graceMinutes: " + project.graceMinutes);

        // 🔥 SAVE
        Project saved = projectPort.create(project);

        log.info("========== AFTER SAVE ==========");
        log.info("saved.id: " + saved.id);
        log.info("saved.workStartTime: " + saved.workStartTime);
        log.info("saved.workEndTime: " + saved.workEndTime);
        log.info("saved.graceMinutes: " + saved.graceMinutes);

        // 👤 RESPONSIBLE (PROJECT MANAGER)
        // Automatically add the responsible PM as a member with role PROJECT_MANAGER
        if (input.responsibleId != null) {
            // Check if the responsible is already in the members list to avoid duplicates
            boolean alreadyInMembers = input.members != null && input.members.stream()
                    .anyMatch(m -> m.userId.equals(input.responsibleId));

            if (!alreadyInMembers) {
                ProjectMember pm = new ProjectMember();
                pm.id = UUID.randomUUID();
                pm.projectId = saved.id;
                pm.userId = input.responsibleId;
                pm.role = "PROJECT_MANAGER";
                pm.createdAt = TimezoneService.utcNow();
                pm.updatedAt = TimezoneService.utcNow();
                log.info("Adding responsible PM userId: " + input.responsibleId + " role: PROJECT_MANAGER");
                projectPort.addMember(pm);
            } else {
                log.info("Responsible PM already in members list, skipping duplicate");
            }
        }

        // 🔥 MEMBERS (TEAM MEMBERS)
        if (input.members != null && !input.members.isEmpty()) {

            log.info("========== ADDING MEMBERS ==========");

            for (ProjectMemberInput member : input.members) {
                // Skip the responsible PM if already auto-added above to avoid duplicates
                if (input.responsibleId != null && member.userId.equals(input.responsibleId)) {
                    log.info("Skipping member userId: " + member.userId + " (already added as responsible PM)");
                    continue;
                }

                ProjectMember pm = new ProjectMember();

                pm.id = UUID.randomUUID();
                pm.projectId = saved.id;
                pm.userId = member.userId;
                pm.role = member.role;
                pm.createdAt = TimezoneService.utcNow();
                pm.updatedAt = TimezoneService.utcNow();

                log.info("Adding member userId: " + member.userId + " role: " + member.role);

                projectPort.addMember(pm);
            }
        }

        outboxPublisher.publish("PROJECT", saved.id, "PROJECT_CREATED",
                "{\"projectId\":\"" + saved.id + "\",\"name\":\"" + escape(saved.name)
                + "\",\"responsibleId\":\"" + saved.responsibleId
                + "\",\"userId\":\"" + saved.responsibleId + "\"}");

        // 🔥 HOLIDAYS
        if (input.holidays != null && !input.holidays.isEmpty()) {
            log.info("========== ADDING HOLIDAYS ==========");
            for (String dStr : input.holidays) {
                try {
                    LocalDate d = LocalDate.parse(dStr);
                    HolidayEntity holiday = HolidayEntity.builder()
                            .id(UUID.randomUUID())
                            .type("PROJECT")
                            .targetId(project.id)
                            .date(d)
                            .name("Feriado del proyecto")
                            .createdAt(LocalDateTime.now())
                            .build();
                    holidayRepository.persist(holiday);
                    log.info("Added holiday: " + d);
                } catch (Exception e) {
                    log.error("Failed to add holiday: " + dStr, e);
                }
            }
        }

        log.info("========== FINAL RETURN ==========");
        log.info("returning project id: " + saved.id);

        return saved;
    }

    private String escape(String s) {
        return s != null ? s.replace("\\", "\\\\").replace("\"", "\\\"") : "";
    }

    // 🔥 SAFE PARSER
    private LocalTime safeTime(Object value) {
        if (value == null) return null;

        try {
            LocalTime parsed = LocalTime.parse(value.toString());
            log.info("Parsed time: " + value + " -> " + parsed);
            return parsed;
        } catch (Exception e) {
            log.warn("Error parsing LocalTime: " + value);
            return null;
        }
    }
}