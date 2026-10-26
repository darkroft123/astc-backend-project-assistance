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

import java.util.Objects;
import java.util.UUID;

import com.smms.assistance.shared.util.TimezoneService;

@ApplicationScoped
public class UpdateProjectUseCase {

    private static final Logger LOG = Logger.getLogger(UpdateProjectUseCase.class);

    private final ProjectPort projectPort;
    private final OutboxEventPublisher outboxPublisher;
    private final HolidayRepository holidayRepository;

    @Inject
    public UpdateProjectUseCase(ProjectPort projectPort, OutboxEventPublisher outboxPublisher, HolidayRepository holidayRepository) {
        this.projectPort = projectPort;
        this.outboxPublisher = outboxPublisher;
        this.holidayRepository = holidayRepository;
    }

    @Transactional
    public Project execute(UUID id, ProjectInput input) {
        Project project = projectPort.findById(id).orElse(null);

        if (project == null) {
            throw new IllegalArgumentException("Project not found: " + id);
        }

        boolean scheduleChanged = (input.workStartTime != null && !Objects.equals(input.workStartTime, project.workStartTime))
                || (input.workEndTime != null && !Objects.equals(input.workEndTime, project.workEndTime))
                || (input.graceMinutes != null && !Objects.equals(input.graceMinutes, project.graceMinutes));

        if (input.name != null) {
            project.name = input.name;
        }
        if (input.description != null) {
            project.description = input.description;
        }
        if (input.status != null) {
            project.status = input.status;
        }
        if (input.startDate != null) {
            project.startDate = input.startDate;
        }
        if (input.endDate != null) {
            project.endDate = input.endDate;
        }
        if (input.budget != null) {
            project.budget = input.budget;
        }
        if (input.currency != null) {
            project.currency = input.currency;
        }

        if (input.workStartTime != null) {
            project.workStartTime = input.workStartTime;
        }
        if (input.workEndTime != null) {
            project.workEndTime = input.workEndTime;
        }
        if (input.graceMinutes != null) {
            project.graceMinutes = input.graceMinutes;
        }
        if (input.absenceCutoffTime != null) {
            project.absenceCutoffTime = input.absenceCutoffTime;
        }
        if (input.timezone != null && TimezoneService.isValidTimezone(input.timezone)) {
            project.timezone = input.timezone;
        }

        if (input.vacationEligibilityDays != null) {
            project.vacationEligibilityDays = input.vacationEligibilityDays;
        }

        if (input.responsibleId != null) {
            project.responsibleId = input.responsibleId;
        }

        project.updatedAt = TimezoneService.utcNow();

        Project updated = projectPort.update(project);

        // Sync members ONLY if input.members was explicitly provided (differential sync)
        if (input.members != null) {
            java.util.List<ProjectMember> existingMembers = projectPort.findMembersByProjectId(id);
            java.util.Map<UUID, ProjectMember> existingMap = new java.util.HashMap<>();
            if (existingMembers != null) {
                for (ProjectMember m : existingMembers) {
                    existingMap.put(m.userId, m);
                }
            }

            java.util.Set<UUID> keepUserIds = new java.util.HashSet<>();
            java.util.Set<UUID> processedUserIds = new java.util.HashSet<>();

            // Handle the responsible PM
            if (input.responsibleId != null) {
                keepUserIds.add(input.responsibleId);
                processedUserIds.add(input.responsibleId);
                ProjectMember existingPm = existingMap.get(input.responsibleId);
                if (existingPm == null) {
                    ProjectMember pm = new ProjectMember();
                    pm.id = UUID.randomUUID();
                    pm.projectId = id;
                    pm.userId = input.responsibleId;
                    pm.role = "PROJECT_MANAGER";
                    pm.createdAt = TimezoneService.utcNow();
                    pm.updatedAt = TimezoneService.utcNow();
                    projectPort.addMember(pm);
                } else if (!"PROJECT_MANAGER".equals(existingPm.role)) {
                    existingPm.role = "PROJECT_MANAGER";
                    existingPm.updatedAt = TimezoneService.utcNow();
                    projectPort.updateMember(existingPm);
                }
            }

            // Sync user members (deduplicated)
            if (!input.members.isEmpty()) {
                for (ProjectMemberInput memberInput : input.members) {
                    if (memberInput.userId == null || processedUserIds.contains(memberInput.userId)) {
                        continue;
                    }
                    processedUserIds.add(memberInput.userId);
                    keepUserIds.add(memberInput.userId);
                    ProjectMember existing = existingMap.get(memberInput.userId);
                    if (existing == null) {
                        ProjectMember pm = new ProjectMember();
                        pm.id = UUID.randomUUID();
                        pm.projectId = id;
                        pm.userId = memberInput.userId;
                        pm.role = memberInput.role;
                        pm.createdAt = TimezoneService.utcNow();
                        pm.updatedAt = TimezoneService.utcNow();
                        projectPort.addMember(pm);
                    } else if (!memberInput.role.equals(existing.role)) {
                        existing.role = memberInput.role;
                        existing.updatedAt = TimezoneService.utcNow();
                        projectPort.updateMember(existing);
                    }
                }
            }

            // Delete members not in the keep list
            if (existingMembers != null) {
                for (ProjectMember m : existingMembers) {
                    if (!keepUserIds.contains(m.userId)) {
                        projectPort.deleteMember(m.id);
                    }
                }
            }
            ProjectMember.flush();
        }

        LOG.infof("[NOTIFICATION] Project UPDATED: projectId=%s, name=%s", id, updated.name);
        outboxPublisher.publish("PROJECT", id, "PROJECT_UPDATED",
                "{\"projectId\":\"" + id + "\",\"name\":\"" + escape(updated.name)
                + "\",\"userId\":\"" + updated.responsibleId + "\"}");

        // 🔥 Only publish SCHEDULE_DEFAULT_CHANGED if schedule values actually changed
        if (scheduleChanged) {
            LOG.infof("[NOTIFICATION] Schedule DEFAULT_CHANGED (via project update): projectId=%s", id);
            outboxPublisher.publish("SCHEDULE", id, "SCHEDULE_DEFAULT_CHANGED",
                    "{\"projectId\":\"" + id + "\",\"name\":\"" + escape(updated.name)
                    + "\",\"workStart\":\"" + updated.workStartTime + "\",\"workEnd\":\"" + updated.workEndTime
                    + "\",\"graceMinutes\":\"" + updated.graceMinutes
                    + "\",\"userId\":\"" + updated.responsibleId + "\"}");
        }

        // 🔥 SYNC HOLIDAYS
        if (input.holidays != null) {
            LOG.info("========== REPLACING HOLIDAYS ==========");
            holidayRepository.deleteByTargetId(id);
            for (String dateStr : input.holidays) {
                try {
                    LocalDate hDate = LocalDate.parse(dateStr);
                    HolidayEntity holiday = HolidayEntity.builder()
                            .id(UUID.randomUUID())
                            .targetId(id)
                            .type("PROJECT")
                            .date(hDate)
                            .name("Feriado " + dateStr)
                            .createdAt(java.time.LocalDateTime.now())
                            .build();
                    holidayRepository.persist(holiday);
                    LOG.info("Added holiday: " + dateStr);
                } catch (Exception e) {
                    LOG.error("Failed to add holiday: " + dateStr, e);
                }
            }
        }

        return updated;
    }

    private String escape(String s) {
        return s != null ? s.replace("\\", "\\\\").replace("\"", "\\\"") : "";
    }
}