package com.smms.assistance.infrastructure.controller.graphql.dto;

import org.eclipse.microprofile.graphql.Input;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Input("ShiftTemplateInput")
public class ShiftTemplateInput {
    public UUID projectId;
    public String name;
    public String shiftType;
    public LocalTime workStartTime;
    public LocalTime workEndTime;
    public Integer graceMinutes;

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

    public Integer rotationWorkDays;
    public Integer rotationRestDays;
    public LocalTime rotationShiftStartTime;
    public LocalTime rotationShiftEndTime;

    public LocalDate validFrom;
    public LocalDate validUntil;

    public LocalTime absenceCutoffTime;
    public String timezone;
}
