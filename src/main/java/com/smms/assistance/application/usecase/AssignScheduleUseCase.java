package com.smms.assistance.application.usecase;

import com.smms.assistance.application.in.AssignSchedulePort;
import com.smms.assistance.domain.entity.ScheduleEntity;
import com.smms.assistance.infrastructure.messaging.OutboxEventPublisher;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class AssignScheduleUseCase implements AssignSchedulePort {

    private static final Logger LOG = Logger.getLogger(AssignScheduleUseCase.class);

    @Inject
    OutboxEventPublisher outboxPublisher;

    @Override
    @Transactional
    public ScheduleEntity execute(UUID userId, UUID projectId, String shiftType,
                                  LocalTime workStartTime, LocalTime workEndTime, Integer graceMinutes,
                                  LocalTime mondayStart, LocalTime mondayEnd,
                                  LocalTime tuesdayStart, LocalTime tuesdayEnd,
                                  LocalTime wednesdayStart, LocalTime wednesdayEnd,
                                  LocalTime thursdayStart, LocalTime thursdayEnd,
                                  LocalTime fridayStart, LocalTime fridayEnd,
                                  LocalTime saturdayStart, LocalTime saturdayEnd,
                                  LocalTime sundayStart, LocalTime sundayEnd,
                                  Integer rotationWorkDays, Integer rotationRestDays,
                                  LocalTime rotationShiftStartTime, LocalTime rotationShiftEndTime,
                                  java.time.LocalDate validFrom, java.time.LocalDate validUntil,
                                  LocalTime absenceCutoffTime, String timezone) {
        Optional<ScheduleEntity> existing = ScheduleEntity
                .find("userId = ?1 and projectId = ?2", userId, projectId)
                .firstResultOptional();

        ScheduleEntity entity;
        if (existing.isPresent()) {
            entity = existing.get();
        } else {
            entity = new ScheduleEntity();
            entity.setId(UUID.randomUUID());
            entity.setUserId(userId);
            entity.setProjectId(projectId);
        }

        entity.setWorkStartTime(workStartTime);
        entity.setWorkEndTime(workEndTime);
        entity.setGraceMinutes(graceMinutes != null ? graceMinutes : 10);
        entity.setShiftType(shiftType);
        
        entity.setMondayStart(mondayStart); entity.setMondayEnd(mondayEnd);
        entity.setTuesdayStart(tuesdayStart); entity.setTuesdayEnd(tuesdayEnd);
        entity.setWednesdayStart(wednesdayStart); entity.setWednesdayEnd(wednesdayEnd);
        entity.setThursdayStart(thursdayStart); entity.setThursdayEnd(thursdayEnd);
        entity.setFridayStart(fridayStart); entity.setFridayEnd(fridayEnd);
        entity.setSaturdayStart(saturdayStart); entity.setSaturdayEnd(saturdayEnd);
        entity.setSundayStart(sundayStart); entity.setSundayEnd(sundayEnd);
        entity.setRotationWorkDays(rotationWorkDays);
        entity.setRotationRestDays(rotationRestDays);
        entity.setRotationShiftStartTime(rotationShiftStartTime);
        entity.setRotationShiftEndTime(rotationShiftEndTime);
        entity.setValidFrom(validFrom);
        entity.setValidUntil(validUntil);
        entity.setAbsenceCutoffTime(absenceCutoffTime);
        entity.setTimezone(timezone);

        if (existing.isPresent()) {
            entity.persistAndFlush();
        } else {
            entity.persist();
        }

        String eventType = existing.isPresent() ? "SCHEDULE_MODIFIED" : "SCHEDULE_ASSIGNED";
        LOG.infof("[NOTIFICATION] Schedule %s: userId=%s, projectId=%s, workStart=%s, workEnd=%s, graceMinutes=%d",
                eventType, userId, projectId, workStartTime, workEndTime, graceMinutes != null ? graceMinutes : 10);
        outboxPublisher.publish("SCHEDULE", entity.getId(), eventType,
                "{\"userId\":\"" + userId + "\",\"projectId\":\"" + projectId
                + "\",\"workStart\":\"" + workStartTime + "\",\"workEnd\":\"" + workEndTime
                + "\",\"graceMinutes\":" + graceMinutes + "}");

        return entity;
    }
}
