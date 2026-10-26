package com.smms.assistance.infrastructure.controller.graphql.dto;

import lombok.*;
import org.eclipse.microprofile.graphql.Type;

import java.util.UUID;

@Type
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShiftTemplateOutput {
    private UUID id;
    private UUID projectId;
    private String name;
    private String shiftType;
    private String workStartTime;
    private String workEndTime;
    private Integer graceMinutes;
    private String mondayStart;
    private String mondayEnd;
    private String tuesdayStart;
    private String tuesdayEnd;
    private String wednesdayStart;
    private String wednesdayEnd;
    private String thursdayStart;
    private String thursdayEnd;
    private String fridayStart;
    private String fridayEnd;
    private String saturdayStart;
    private String saturdayEnd;
    private String sundayStart;
    private String sundayEnd;
    private Integer rotationWorkDays;
    private Integer rotationRestDays;
    private String rotationShiftStartTime;
    private String rotationShiftEndTime;
    private String validFrom;
    private String validUntil;
    private String absenceCutoffTime;
    private String timezone;
    private String createdAt;
}
