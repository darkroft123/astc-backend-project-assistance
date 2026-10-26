package com.smms.assistance.infrastructure.controller.graphql.dto;

import org.eclipse.microprofile.graphql.Input;

import java.time.LocalTime;
import java.time.LocalDate;
import java.util.UUID;

@Input("ScheduleInput")
public class ScheduleInput {
    public UUID userId;
    public UUID projectId;
    public LocalTime workStartTime;
    public LocalTime workEndTime;
    public Integer graceMinutes;
    public String shiftType;
    
    // Flexible fields
    public LocalTime mondayStart;
    public LocalTime mondayEnd;
    public LocalTime tuesdayStart;
    public LocalTime tuesdayEnd;
    public LocalTime wednesdayStart;
    public LocalTime wednesdayEnd;
    public LocalTime thursdayStart;
    public LocalTime thursdayEnd;
    public LocalTime fridayStart;
    public LocalTime fridayEnd;
    public LocalTime saturdayStart;
    public LocalTime saturdayEnd;
    public LocalTime sundayStart;
    public LocalTime sundayEnd;

    // Rotation fields
    public Integer rotationWorkDays;
    public Integer rotationRestDays;
    public LocalTime rotationShiftStartTime;
    public LocalTime rotationShiftEndTime;

    // Transitory fields
    public LocalDate validFrom;
    public LocalDate validUntil;

    // Settings
    public LocalTime absenceCutoffTime;
    public String timezone;
}
