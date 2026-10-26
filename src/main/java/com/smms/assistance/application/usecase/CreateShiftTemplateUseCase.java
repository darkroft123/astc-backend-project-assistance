package com.smms.assistance.application.usecase;

import com.smms.assistance.domain.entity.ShiftTemplateEntity;
import com.smms.assistance.infrastructure.messaging.OutboxEventPublisher;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@ApplicationScoped
public class CreateShiftTemplateUseCase {

    private static final Logger LOG = Logger.getLogger(CreateShiftTemplateUseCase.class);

    @Inject
    OutboxEventPublisher outboxPublisher;

    @Transactional
    public ShiftTemplateEntity execute(UUID projectId, String name, String shiftType,
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
                                        LocalDate validFrom, LocalDate validUntil,
                                        LocalTime absenceCutoffTime, String timezone) {
        ShiftTemplateEntity entity = ShiftTemplateEntity.builder()
                .id(UUID.randomUUID())
                .projectId(projectId)
                .name(name)
                .shiftType(shiftType)
                .workStartTime(workStartTime)
                .workEndTime(workEndTime)
                .graceMinutes(graceMinutes != null ? graceMinutes : 10)
                .mondayStart(mondayStart).mondayEnd(mondayEnd)
                .tuesdayStart(tuesdayStart).tuesdayEnd(tuesdayEnd)
                .wednesdayStart(wednesdayStart).wednesdayEnd(wednesdayEnd)
                .thursdayStart(thursdayStart).thursdayEnd(thursdayEnd)
                .fridayStart(fridayStart).fridayEnd(fridayEnd)
                .saturdayStart(saturdayStart).saturdayEnd(saturdayEnd)
                .sundayStart(sundayStart).sundayEnd(sundayEnd)
                .rotationWorkDays(rotationWorkDays)
                .rotationRestDays(rotationRestDays)
                .rotationShiftStartTime(rotationShiftStartTime)
                .rotationShiftEndTime(rotationShiftEndTime)
                .validFrom(validFrom)
                .validUntil(validUntil)
                .absenceCutoffTime(absenceCutoffTime)
                .timezone(timezone)
                .build();
        entity.persist();

        LOG.infof("[SHIFT] Template created: id=%s, projectId=%s, type=%s, name=%s",
                entity.getId(), projectId, shiftType, name);

        return entity;
    }
}
