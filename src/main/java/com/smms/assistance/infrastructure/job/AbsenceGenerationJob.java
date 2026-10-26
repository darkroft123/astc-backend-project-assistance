package com.smms.assistance.infrastructure.job;

import com.smms.assistance.application.in.HasApprovedVacationPort;
import com.smms.assistance.domain.entity.*;
import com.smms.assistance.infrastructure.repository.AbsenceRepository;
import com.smms.assistance.infrastructure.repository.AttendanceRepository;
import com.smms.assistance.infrastructure.repository.HolidayRepository;
import com.smms.assistance.shared.util.TimezoneService;
import com.smms.assistance.application.service.ScheduleEvaluationService;
import com.smms.assistance.infrastructure.controller.graphql.dto.EffectiveScheduleOutput;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.ScheduledExecution;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class AbsenceGenerationJob {

    private static final Logger LOG = Logger.getLogger(AbsenceGenerationJob.class);

    @Inject
    AttendanceRepository attendanceRepository;

    @Inject
    AbsenceRepository absenceRepository;

    @Inject
    HasApprovedVacationPort hasApprovedVacationPort;

    @Inject
    HolidayRepository holidayRepository;

    @Inject
    ScheduleEvaluationService scheduleEvaluationService;

    @Inject
    EntityManager entityManager;

    @ConfigProperty(name = "app.absence.job.cutoff-hour", defaultValue = "9")
    int cutoffHour;

    @ConfigProperty(name = "app.absence.job.cutoff-minute", defaultValue = "30")
    int cutoffMinute;

    @Scheduled(cron = "{app.absence.job.cron}")
    @Transactional
    void generateAbsences(ScheduledExecution execution) {
        LOG.info("========================================");
        LOG.infof("[AbsenceJob] STARTED — %s", execution.getScheduledFireTime());
        LOG.info("========================================");

        int absencesCreated = 0;
        int projectsProcessed = 0;
        int usersChecked = 0;

        List<Project> activeProjects = Project.find("status = ?1 and deletedAt is null", "ACTIVE").list();
        LOG.infof("[AbsenceJob] Active projects found: %d", activeProjects.size());

        if (activeProjects.isEmpty()) {
            LOG.info("No active projects found.");
            return;
        }

        LocalDate localToday = LocalDate.now();

        // 1. Preload all schedule overrides
        List<ScheduleEntity> overridesList = ScheduleEntity.listAll();
        java.util.Map<String, ScheduleEntity> overridesMap = new java.util.HashMap<>();
        for (ScheduleEntity override : overridesList) {
            overridesMap.put(override.getUserId() + "_" + override.getProjectId(), override);
        }

        // 2. Preload all approved vacations for the range [localToday - 2, localToday + 2]
        List<VacationRequestEntity> vacations = entityManager.createQuery(
                "select v from VacationRequestEntity v where v.status = 'APPROVED' and v.startDate <= :to and v.endDate >= :from", VacationRequestEntity.class)
                .setParameter("from", localToday.minusDays(2))
                .setParameter("to", localToday.plusDays(2))
                .getResultList();

        java.util.Set<String> userVacationDates = new java.util.HashSet<>();
        for (VacationRequestEntity v : vacations) {
            LocalDate start = v.getStartDate();
            LocalDate end = v.getEndDate();
            for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
                userVacationDates.add(v.getUserId() + "_" + d);
            }
        }

        // 3. Preload all holidays for the range [localToday - 2, localToday + 2]
        List<HolidayEntity> holidays = entityManager.createQuery(
                "select h from HolidayEntity h where h.date >= :from and h.date <= :to", HolidayEntity.class)
                .setParameter("from", localToday.minusDays(2))
                .setParameter("to", localToday.plusDays(2))
                .getResultList();

        java.util.Map<LocalDate, List<HolidayEntity>> globalHolidaysMap = new java.util.HashMap<>();
        java.util.Map<String, List<HolidayEntity>> projectHolidaysMap = new java.util.HashMap<>();
        java.util.Map<String, List<HolidayEntity>> userHolidaysMap = new java.util.HashMap<>();

        for (HolidayEntity h : holidays) {
            if ("GLOBAL".equals(h.getType())) {
                globalHolidaysMap.computeIfAbsent(h.getDate(), k -> new java.util.ArrayList<>()).add(h);
            } else if ("PROJECT".equals(h.getType()) && h.getTargetId() != null) {
                projectHolidaysMap.computeIfAbsent(h.getTargetId() + "_" + h.getDate(), k -> new java.util.ArrayList<>()).add(h);
            } else if (h.getTargetId() != null) {
                userHolidaysMap.computeIfAbsent(h.getTargetId() + "_" + h.getDate(), k -> new java.util.ArrayList<>()).add(h);
            }
        }

        for (Project project : activeProjects) {
            projectsProcessed++;

            String tz = (project.timezone != null && TimezoneService.isValidTimezone(project.timezone))
                    ? project.timezone
                    : TimezoneService.defaultTimezone();

            LocalDate today = TimezoneService.todayAtZone(tz);
            LocalTime now = TimezoneService.nowAtZone(tz);
            List<ProjectMember> members = ProjectMember.find("projectId = ?1", project.id).list();
            LOG.infof("[AbsenceJob] Project %s (%s): %d members, checking date=%s, tz=%s",
                    project.name, project.id, members.size(), today, tz);

            java.util.List<UUID> memberIds = members.stream().map(m -> m.userId).collect(java.util.stream.Collectors.toList());
            java.util.Set<UUID> usersWithAttendance = new java.util.HashSet<>();
            java.util.Set<UUID> usersWithAbsence = new java.util.HashSet<>();

            if (!memberIds.isEmpty()) {
                usersWithAttendance.addAll(entityManager.createQuery(
                        "SELECT a.userId FROM AttendanceEntity a WHERE a.projectId = :projectId AND a.date = :today AND a.status NOT IN ('OUTSIDE_SCHEDULE', 'EARLY') AND a.userId IN :userIds", UUID.class)
                        .setParameter("projectId", project.id)
                        .setParameter("today", today)
                        .setParameter("userIds", memberIds)
                        .getResultList());

                usersWithAbsence.addAll(entityManager.createQuery(
                        "SELECT a.userId FROM AbsenceEntity a WHERE a.projectId = :projectId AND a.date = :today AND a.userId IN :userIds", UUID.class)
                        .setParameter("projectId", project.id)
                        .setParameter("today", today)
                        .setParameter("userIds", memberIds)
                        .getResultList());
            }

            for (ProjectMember member : members) {
                usersChecked++;
                UUID userId = member.userId;

                ScheduleEntity override = overridesMap.get(userId + "_" + project.id);

                // Gather relevant holidays for today
                List<HolidayEntity> relevantHolidays = new java.util.ArrayList<>();
                List<HolidayEntity> gHols = globalHolidaysMap.get(today);
                if (gHols != null) relevantHolidays.addAll(gHols);
                List<HolidayEntity> pHols = projectHolidaysMap.get(project.id + "_" + today);
                if (pHols != null) relevantHolidays.addAll(pHols);
                List<HolidayEntity> uHols = userHolidaysMap.get(userId + "_" + today);
                if (uHols != null) relevantHolidays.addAll(uHols);

                EffectiveScheduleOutput schedule = scheduleEvaluationService.getEffectiveSchedule(project, relevantHolidays, override, today);

                if (!schedule.isWorkDay()) {
                    LOG.infof("[AbsenceJob] User %s: Today %s is not a work day, skipping", userId, today);
                    continue;
                }

                // Interval check: if shift hasn't started yet, skip (avoid false absences for night shifts)
                LocalTime workStart = schedule.getExpectedStartTime();
                if (workStart != null) {
                    LocalTime cutoff = schedule.getAbsenceCutoffTime();

                    // Night shift detection (workEnd < workStart, e.g. 22:00-06:00)
                    LocalTime workEnd = schedule.getExpectedEndTime();
                    if (workEnd != null && workEnd.isBefore(workStart)) {
                        // Night shift: check if now is within the valid window
                        // [workStart, 23:59] or [00:00, workEnd]
                        boolean inSameEvening = !now.isBefore(workStart);
                        boolean inAfterMidnight = !now.isAfter(workEnd) && !now.isBefore(java.time.LocalTime.MIDNIGHT);
                        if (!inSameEvening && !inAfterMidnight) {
                            LOG.infof("[AbsenceJob] User %s: current time %s is outside night shift window [%s, 23:59] ∪ [00:00, %s], skipping",
                                    userId, now, workStart, workEnd);
                            continue;
                        }
                        // Skip while working (after midnight) — absence caught on start date
                        if (inAfterMidnight) {
                            LOG.infof("[AbsenceJob] User %s: current time %s is within night shift post-midnight window, skipping",
                                    userId, now);
                            continue;
                        }
                        // Same evening: use cutoff
                        if (now.isBefore(cutoff)) {
                            LOG.infof("[AbsenceJob] User %s: current time %s is before cutoff %s for night shift, skipping",
                                    userId, now, cutoff);
                            continue;
                        }
                    } else {
                        // Day shift: always skip if before work start (regardless of cutoff)
                        if (workStart != null && now.isBefore(workStart)) {
                            LOG.infof("[AbsenceJob] User %s: current time %s is before work start %s, skipping",
                                    userId, now, workStart);
                            continue;
                        }
                        if (now.isBefore(cutoff)) {
                            LOG.infof("[AbsenceJob] User %s: current time %s is before user cutoff %s, skipping",
                                    userId, now, cutoff);
                            continue;
                        }
                    }
                } else {
                    LocalTime cutoff = schedule.getAbsenceCutoffTime();
                    if (now.isBefore(cutoff)) {
                        LOG.infof("[AbsenceJob] User %s: current time %s is before user cutoff %s, skipping",
                                userId, now, cutoff);
                        continue;
                    }
                }

                if (usersWithAttendance.contains(userId)) {
                    LOG.debugf("[AbsenceJob] User %s has attendance for project %s on %s, skipping",
                            userId, project.id, today);
                    continue;
                }

                if (usersWithAbsence.contains(userId)) {
                    LOG.debugf("[AbsenceJob] User %s already has absence for project %s on %s, skipping",
                            userId, project.id, today);
                    continue;
                }

                boolean isExclude = uHols != null && uHols.stream().anyMatch(h -> "USER_EXCLUDE".equals(h.getType()));
                boolean isInclude = uHols != null && uHols.stream().anyMatch(h -> "USER_INCLUDE".equals(h.getType()));
                boolean isCollectionHoliday = (gHols != null && !gHols.isEmpty()) || (pHols != null && !pHols.isEmpty());
                boolean isHoliday = !isExclude && (isInclude || isCollectionHoliday);

                if (isHoliday) {
                    LOG.debugf("[AbsenceJob] Today %s is a holiday for project %s (%s), skipping",
                            today, project.name, project.id);
                    continue;
                }

                boolean onVacation = userVacationDates.contains(userId + "_" + today);
                if (onVacation) {
                    LOG.infof("[AbsenceJob] SKIPPED absence — user %s has approved vacation on %s for project %s (%s)",
                            userId, today, project.name, project.id);
                    continue;
                }

                AbsenceEntity absence = AbsenceEntity.builder()
                        .id(UUID.randomUUID())
                        .userId(userId)
                        .projectId(project.id)
                        .date(today)
                        .type("FALTA_AUTOMATICA")
                        .justified(false)
                        .build();

                absenceRepository.persist(absence);
                absencesCreated++;

                LOG.infof("[AbsenceJob] ABSENCE CREATED — user=%s, project=%s (%s), date=%s",
                        userId, project.name, project.id, today);
            }
        }

        LOG.info("========================================");
        LOG.infof("[AbsenceJob] COMPLETED — projects=%d, users=%d, absences=%d",
                projectsProcessed, usersChecked, absencesCreated);
        LOG.info("========================================");
    }
}
