package com.smms.assistance.application.in;

import com.smms.assistance.domain.entity.ScheduleEntity;
import java.util.UUID;

public interface AssignSchedulePort {
    ScheduleEntity execute(UUID userId, UUID projectId, String shiftType,
                           java.time.LocalTime workStartTime, java.time.LocalTime workEndTime, Integer graceMinutes,
                           java.time.LocalTime mondayStart, java.time.LocalTime mondayEnd,
                           java.time.LocalTime tuesdayStart, java.time.LocalTime tuesdayEnd,
                           java.time.LocalTime wednesdayStart, java.time.LocalTime wednesdayEnd,
                           java.time.LocalTime thursdayStart, java.time.LocalTime thursdayEnd,
                           java.time.LocalTime fridayStart, java.time.LocalTime fridayEnd,
                           java.time.LocalTime saturdayStart, java.time.LocalTime saturdayEnd,
                           java.time.LocalTime sundayStart, java.time.LocalTime sundayEnd,
                           Integer rotationWorkDays, Integer rotationRestDays,
                           java.time.LocalTime rotationShiftStartTime, java.time.LocalTime rotationShiftEndTime,
                           java.time.LocalDate validFrom, java.time.LocalDate validUntil,
                           java.time.LocalTime absenceCutoffTime, String timezone);
}
