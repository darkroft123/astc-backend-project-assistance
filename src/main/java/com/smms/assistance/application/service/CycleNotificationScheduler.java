package com.smms.assistance.application.service;

import com.smms.assistance.domain.entity.Notification;
import com.smms.assistance.domain.entity.ProjectMember;
import com.smms.assistance.infrastructure.controller.graphql.dto.EffectiveScheduleOutput;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import com.smms.assistance.domain.entity.ScheduleEntity;
import com.smms.assistance.domain.entity.HolidayEntity;
import org.jboss.logging.Logger;

@ApplicationScoped
public class CycleNotificationScheduler {

    private static final Logger LOG = Logger.getLogger(CycleNotificationScheduler.class);

    @Inject
    ScheduleEvaluationService scheduleEvaluationService;

    @Inject
    jakarta.persistence.EntityManager entityManager;

    @Inject
    com.smms.assistance.infrastructure.repository.HolidayRepository holidayRepository;

    // Runs every day at 1:00 AM
    @Scheduled(cron = "0 0 1 * * ?")
    @Transactional
    public void evaluateCycleChanges() {
        LOG.info("Starting daily evaluation for cycle notifications...sssr");
        long startTime = System.currentTimeMillis();

        List<ProjectMember> members = ProjectMember.listAll();
        if (members.isEmpty()) {
            LOG.info("No members found to evaluate.");
            return;
        }

        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);

        // 1. Preload all projects
        List<com.smms.assistance.domain.entity.Project> projectsList = com.smms.assistance.domain.entity.Project.listAll();
        java.util.Map<UUID, com.smms.assistance.domain.entity.Project> projectsMap = projectsList.stream()
                .collect(Collectors.toMap(p -> p.id, p -> p));

        // 2. Preload all overrides
        List<ScheduleEntity> overridesList = ScheduleEntity.listAll();
        java.util.Map<String, ScheduleEntity> overridesMap = new java.util.HashMap<>();
        for (ScheduleEntity override : overridesList) {
            overridesMap.put(override.getUserId() + "_" + override.getProjectId(), override);
        }

        // 3. Preload all holidays for today & tomorrow
        List<HolidayEntity> holidays = entityManager.createQuery(
                "select h from HolidayEntity h where h.date >= :from and h.date <= :to", HolidayEntity.class)
                .setParameter("from", today)
                .setParameter("to", tomorrow)
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

        int notificationsSent = 0;

        for (ProjectMember member : members) {
            if (member.userId == null || member.projectId == null) continue;

            com.smms.assistance.domain.entity.Project project = projectsMap.get(member.projectId);
            if (project == null) continue;

            ScheduleEntity override = overridesMap.get(member.userId + "_" + member.projectId);

            // Gather today's relevant holidays
            List<HolidayEntity> relevantToday = new java.util.ArrayList<>();
            List<HolidayEntity> gToday = globalHolidaysMap.get(today);
            if (gToday != null) relevantToday.addAll(gToday);
            List<HolidayEntity> pToday = projectHolidaysMap.get(member.projectId + "_" + today);
            if (pToday != null) relevantToday.addAll(pToday);
            List<HolidayEntity> uToday = userHolidaysMap.get(member.userId + "_" + today);
            if (uToday != null) relevantToday.addAll(uToday);

            // Gather tomorrow's relevant holidays
            List<HolidayEntity> relevantTomorrow = new java.util.ArrayList<>();
            List<HolidayEntity> gTom = globalHolidaysMap.get(tomorrow);
            if (gTom != null) relevantTomorrow.addAll(gTom);
            List<HolidayEntity> pTom = projectHolidaysMap.get(member.projectId + "_" + tomorrow);
            if (pTom != null) relevantTomorrow.addAll(pTom);
            List<HolidayEntity> uTom = userHolidaysMap.get(member.userId + "_" + tomorrow);
            if (uTom != null) relevantTomorrow.addAll(uTom);

            EffectiveScheduleOutput todaySchedule = scheduleEvaluationService.getEffectiveSchedule(project, relevantToday, override, today);
            EffectiveScheduleOutput tomorrowSchedule = scheduleEvaluationService.getEffectiveSchedule(project, relevantTomorrow, override, tomorrow);

            // If today is a rest day, but tomorrow is a work day, the work cycle starts tomorrow!
            if (!todaySchedule.isWorkDay() && tomorrowSchedule.isWorkDay()) {
                sendNotification(member.userId, member.projectId, tomorrowSchedule);
                notificationsSent++;
            }
            // If today is a work day, but tomorrow is a rest day, the rest cycle starts tomorrow!
            else if (todaySchedule.isWorkDay() && !tomorrowSchedule.isWorkDay()) {
                sendRestNotification(member.userId, member.projectId);
                notificationsSent++;
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        LOG.infof("Finished evaluating cycle changes. Duration: %d ms. Notifications generated: %d", duration, notificationsSent);
    }


    private void sendNotification(UUID userId, UUID projectId, EffectiveScheduleOutput schedule) {
        String expectedStart = schedule.getExpectedStartTime() != null ? schedule.getExpectedStartTime().toString() : "00:00";
        String message = String.format("Aviso: Tu ciclo de trabajo comienza mañana a las %s.", expectedStart);

        // Deduplication: check if CYCLE_START notification already exists for this user+project today
        LocalDate today = LocalDate.now();
        long existing = Notification.count(
                "userId = ?1 AND type = ?2 AND referenceId = ?3 AND createdAt >= ?4 AND createdAt < ?5",
                userId, "CYCLE_START", projectId, today.atStartOfDay(), today.plusDays(1).atStartOfDay());
        if (existing > 0) {
            LOG.infof("[CycleNotification] CYCLE_START notification already exists for user %s project %s, skipping", userId, projectId);
            return;
        }

        Notification notification = new Notification(
                UUID.randomUUID(),
                userId,
                "CYCLE_START",
                "INFO",
                "Inicio de Ciclo Laboral",
                message,
                "PROJECT",
                projectId,
                Instant.now().atZone(ZoneOffset.UTC).toLocalDateTime()
        );
        notification.persist();
    }

    private void sendRestNotification(UUID userId, UUID projectId) {
        String message = "Aviso: Tu ciclo de trabajo termina hoy. A partir de mañana inicia tu periodo de descanso.";

        // Deduplication: check if CYCLE_END notification already exists for this user+project today
        LocalDate today = LocalDate.now();
        long existing = Notification.count(
                "userId = ?1 AND type = ?2 AND referenceId = ?3 AND createdAt >= ?4 AND createdAt < ?5",
                userId, "CYCLE_END", projectId, today.atStartOfDay(), today.plusDays(1).atStartOfDay());
        if (existing > 0) {
            LOG.infof("[CycleNotification] CYCLE_END notification already exists for user %s project %s, skipping", userId, projectId);
            return;
        }

        Notification notification = new Notification(
                UUID.randomUUID(),
                userId,
                "CYCLE_END",
                "INFO",
                "Inicio de Descanso",
                message,
                "PROJECT",
                projectId,
                Instant.now().atZone(ZoneOffset.UTC).toLocalDateTime()
        );
        notification.persist();
    }
}
