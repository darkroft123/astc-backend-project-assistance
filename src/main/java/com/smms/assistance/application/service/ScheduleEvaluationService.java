package com.smms.assistance.application.service;

import com.smms.assistance.domain.entity.Project;
import com.smms.assistance.domain.entity.ScheduleEntity;
import com.smms.assistance.domain.entity.HolidayEntity;
import com.smms.assistance.domain.entity.ShiftTemplateEntity;
import com.smms.assistance.infrastructure.controller.graphql.dto.EffectiveScheduleOutput;
import com.smms.assistance.infrastructure.repository.HolidayRepository;
import com.smms.assistance.infrastructure.repository.ProjectRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jboss.logging.Logger;

@ApplicationScoped
public class ScheduleEvaluationService {

    private static final Logger LOG = Logger.getLogger(ScheduleEvaluationService.class);

    @Inject
    ProjectRepository projectRepository;

    @Inject
    HolidayRepository holidayRepository;

    public EffectiveScheduleOutput getEffectiveSchedule(UUID userId, UUID projectId, LocalDate date) {
        Optional<Project> optionalProject = projectRepository.findById(projectId);
        if (!optionalProject.isPresent()) {
            return fallbackSchedule();
        }
        Project project = optionalProject.get();
        List<HolidayEntity> relevant = holidayRepository.findRelevantInRange(userId, projectId, date, date);
        ScheduleEntity override = ScheduleEntity.find("userId = ?1 and projectId = ?2", userId, projectId).firstResult();
        return getEffectiveSchedule(project, relevant, override, date);
    }

    public EffectiveScheduleOutput getEffectiveSchedule(
            Project project,
            List<HolidayEntity> relevant,
            ScheduleEntity override,
            LocalDate date
    ) {
        HolidayEntity userExclude = null;
        HolidayEntity userInclude = null;
        HolidayEntity projectHoliday = null;
        HolidayEntity globalHoliday = null;

        if (relevant != null) {
            for (HolidayEntity h : relevant) {
                String type = h.getType();
                if ("USER_EXCLUDE".equals(type)) {
                    userExclude = h;
                } else if ("USER_INCLUDE".equals(type)) {
                    userInclude = h;
                } else if ("PROJECT".equals(type)) {
                    projectHoliday = h;
                } else if ("GLOBAL".equals(type)) {
                    globalHoliday = h;
                }
            }
        }

        boolean isHoliday = false;

        if (userInclude != null) {
            isHoliday = true; // User specifically has a free day
        } else if (userExclude == null) {
            if (projectHoliday != null || globalHoliday != null) {
                isHoliday = true; // Holiday applies to user
            }
        }

        // 2. Evaluate normal schedule first
        EffectiveScheduleOutput scheduleOut = null;
        if (override != null) {
            scheduleOut = evaluateOverride(override, date, project.timezone);
        } else {
            DayOfWeek fallbackDay = date.getDayOfWeek();
            boolean isWorkDay = project.workStartTime != null
                    && fallbackDay != DayOfWeek.SATURDAY && fallbackDay != DayOfWeek.SUNDAY;
            scheduleOut = EffectiveScheduleOutput.builder()
                    .workDay(isWorkDay)
                    .expectedStartTime(isWorkDay ? project.workStartTime : null)
                    .expectedEndTime(isWorkDay ? project.workEndTime : null)
                    .graceMinutes(project.graceMinutes != null ? project.graceMinutes : 10)
                    .absenceCutoffTime(project.absenceCutoffTime != null ? project.absenceCutoffTime : defaultCutoff(project.workStartTime, project.graceMinutes))
                    .timezone(project.timezone != null ? project.timezone : "America/Lima")
                    .shiftType("REGULAR")
                    .build();
        }

        // 3. Apply holiday override on the resulting schedule
        if (isHoliday) {
            scheduleOut.setWorkDay(false);
        }

        return scheduleOut;
    }

    private LocalTime defaultCutoff(LocalTime workStart, Integer graceMinutes) {
        if (workStart == null) return LocalTime.of(9, 30);
        return workStart.plusMinutes(graceMinutes != null ? graceMinutes + 30 : 40);
    }

    private boolean isPermanentOrRegular(String st) {
        return "PERMANENT".equals(st) || "REGULAR".equals(st);
    }

    private EffectiveScheduleOutput evaluateOverride(ScheduleEntity s, LocalDate date, String defaultTimezone) {
        String st = s.getShiftType() != null ? s.getShiftType() : "REGULAR";
        String tz = s.getTimezone() != null ? s.getTimezone() : defaultTimezone;
        Integer grace = s.getGraceMinutes() != null ? s.getGraceMinutes() : 10;
        LocalTime cutoff = s.getAbsenceCutoffTime() != null ? s.getAbsenceCutoffTime() : defaultCutoff(s.getWorkStartTime(), s.getGraceMinutes());

        if (st.equals("TRANSITORY")) {
            if (s.getValidFrom() != null && date.isBefore(s.getValidFrom())) return restDay(grace, cutoff, tz, st);
            if (s.getValidUntil() != null && date.isAfter(s.getValidUntil())) return restDay(grace, cutoff, tz, st);
            return workDay(s.getWorkStartTime(), s.getWorkEndTime(), grace, cutoff, tz, st);
        }

        if (st.equals("FLEXIBLE")) {
            return evaluateFlexible(
                    date, st,
                    s.getMondayStart(), s.getMondayEnd(),
                    s.getTuesdayStart(), s.getTuesdayEnd(),
                    s.getWednesdayStart(), s.getWednesdayEnd(),
                    s.getThursdayStart(), s.getThursdayEnd(),
                    s.getFridayStart(), s.getFridayEnd(),
                    s.getSaturdayStart(), s.getSaturdayEnd(),
                    s.getSundayStart(), s.getSundayEnd(),
                    grace, cutoff, tz
            );
        }

        if (st.startsWith("ROTATING")) {
            return evaluateRotating(date, st, s.getValidFrom(), s.getRotationWorkDays(), s.getRotationRestDays(),
                    s.getRotationShiftStartTime(), s.getRotationShiftEndTime(), grace, cutoff, tz);
        }

        // REGULAR / PERMANENT: Lunes a Viernes trabajan, Sábado y Domingo descansan
        return evaluateRegular(date, s.getWorkStartTime(), s.getWorkEndTime(), grace, cutoff, tz, st);
    }

    private EffectiveScheduleOutput evaluateTemplate(ShiftTemplateEntity t, LocalDate date, String defaultTimezone) {
        String st = t.getShiftType() != null ? t.getShiftType() : "REGULAR";
        String tz = t.getTimezone() != null ? t.getTimezone() : defaultTimezone;
        Integer grace = t.getGraceMinutes() != null ? t.getGraceMinutes() : 10;
        LocalTime cutoff = t.getAbsenceCutoffTime() != null ? t.getAbsenceCutoffTime() : defaultCutoff(t.getWorkStartTime(), t.getGraceMinutes());

        if (st.equals("TRANSITORY")) {
            if (t.getValidFrom() != null && date.isBefore(t.getValidFrom())) return restDay(grace, cutoff, tz, st);
            if (t.getValidUntil() != null && date.isAfter(t.getValidUntil())) return restDay(grace, cutoff, tz, st);
            return workDay(t.getWorkStartTime(), t.getWorkEndTime(), grace, cutoff, tz, st);
        }

        if (st.equals("FLEXIBLE")) {
            return evaluateFlexible(
                    date, st,
                    t.getMondayStart(), t.getMondayEnd(),
                    t.getTuesdayStart(), t.getTuesdayEnd(),
                    t.getWednesdayStart(), t.getWednesdayEnd(),
                    t.getThursdayStart(), t.getThursdayEnd(),
                    t.getFridayStart(), t.getFridayEnd(),
                    t.getSaturdayStart(), t.getSaturdayEnd(),
                    t.getSundayStart(), t.getSundayEnd(),
                    grace, cutoff, tz
            );
        }

        if (st.startsWith("ROTATING")) {
            return evaluateRotating(date, st, t.getValidFrom(), t.getRotationWorkDays(), t.getRotationRestDays(),
                    t.getRotationShiftStartTime(), t.getRotationShiftEndTime(), grace, cutoff, tz);
        }

        // REGULAR / PERMANENT: Lunes a Viernes trabajan, Sábado y Domingo descansan
        return evaluateRegular(date, t.getWorkStartTime(), t.getWorkEndTime(), grace, cutoff, tz, st);
    }

    private EffectiveScheduleOutput evaluateRegular(LocalDate date, LocalTime start, LocalTime end,
                                                     Integer grace, LocalTime cutoff, String tz, String st) {
        DayOfWeek day = date.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return restDay(grace, cutoff, tz, st);
        }
        if (start == null) return restDay(grace, cutoff, tz, st);
        return workDay(start, end, grace, cutoff, tz, st);
    }

    private EffectiveScheduleOutput evaluateFlexible(LocalDate date, String shiftType,
                                                     LocalTime monS, LocalTime monE,
                                                     LocalTime tueS, LocalTime tueE,
                                                     LocalTime wedS, LocalTime wedE,
                                                     LocalTime thuS, LocalTime thuE,
                                                     LocalTime friS, LocalTime friE,
                                                     LocalTime satS, LocalTime satE,
                                                     LocalTime sunS, LocalTime sunE,
                                                     Integer grace, LocalTime cutoff, String tz) {
        switch (date.getDayOfWeek()) {
            case MONDAY: return monS != null ? workDay(monS, monE, grace, cutoff, tz, shiftType) : restDay(grace, cutoff, tz, shiftType);
            case TUESDAY: return tueS != null ? workDay(tueS, tueE, grace, cutoff, tz, shiftType) : restDay(grace, cutoff, tz, shiftType);
            case WEDNESDAY: return wedS != null ? workDay(wedS, wedE, grace, cutoff, tz, shiftType) : restDay(grace, cutoff, tz, shiftType);
            case THURSDAY: return thuS != null ? workDay(thuS, thuE, grace, cutoff, tz, shiftType) : restDay(grace, cutoff, tz, shiftType);
            case FRIDAY: return friS != null ? workDay(friS, friE, grace, cutoff, tz, shiftType) : restDay(grace, cutoff, tz, shiftType);
            case SATURDAY: return satS != null ? workDay(satS, satE, grace, cutoff, tz, shiftType) : restDay(grace, cutoff, tz, shiftType);
            case SUNDAY: return sunS != null ? workDay(sunS, sunE, grace, cutoff, tz, shiftType) : restDay(grace, cutoff, tz, shiftType);
            default: return restDay(grace, cutoff, tz, shiftType);
        }
    }

    private EffectiveScheduleOutput evaluateRotating(LocalDate date, String shiftType, LocalDate anchor, Integer wDays, Integer rDays,
                                                     LocalTime start, LocalTime end, Integer grace, LocalTime cutoff, String tz) {
        if (wDays == null || rDays == null || start == null) {
            LOG.warnf("ROTATING shift misconfigured: wDays=%s, rDays=%s, start=%s, anchor=%s", wDays, rDays, start, anchor);
            return restDay(grace, cutoff, tz, shiftType);
        }
        if (anchor == null) {
            LOG.warnf("ROTATING shift has no anchor date (validFrom). Defaulting anchor to current date. wDays=%d, rDays=%d", wDays, rDays);
            anchor = LocalDate.now();
        }
        
        long daysDiff = ChronoUnit.DAYS.between(anchor, date);
        if (daysDiff < 0) {
            return restDay(grace, cutoff, tz, shiftType); // Before rotation starts
        }
        
        int cycleLength = wDays + rDays;
        long dayInCycle = daysDiff % cycleLength;
        
        if (dayInCycle < wDays) {
            return workDay(start, end, grace, cutoff, tz, shiftType);
        } else {
            return restDay(grace, cutoff, tz, shiftType);
        }
    }

    private EffectiveScheduleOutput workDay(LocalTime start, LocalTime end, Integer grace, LocalTime cutoff, String tz, String shiftType) {
        return EffectiveScheduleOutput.builder()
                .workDay(true)
                .expectedStartTime(start)
                .expectedEndTime(end)
                .graceMinutes(grace)
                .absenceCutoffTime(cutoff)
                .timezone(tz)
                .shiftType(shiftType)
                .build();
    }

    private EffectiveScheduleOutput restDay(Integer grace, LocalTime cutoff, String tz, String shiftType) {
        return EffectiveScheduleOutput.builder()
                .workDay(false)
                .expectedStartTime(null)
                .expectedEndTime(null)
                .graceMinutes(grace)
                .absenceCutoffTime(cutoff)
                .timezone(tz)
                .shiftType(shiftType)
                .build();
    }

    private EffectiveScheduleOutput fallbackSchedule() {
        return EffectiveScheduleOutput.builder()
                .workDay(false)
                .expectedStartTime(null)
                .expectedEndTime(null)
                .graceMinutes(10)
                .absenceCutoffTime(LocalTime.of(9, 30))
                .timezone("America/Lima")
                .shiftType("REGULAR")
                .build();
    }
}
