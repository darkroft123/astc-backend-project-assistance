package com.smms.assistance.infrastructure.controller.graphql.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EffectiveScheduleOutput {
    private boolean workDay;
    private LocalTime expectedStartTime;
    private LocalTime expectedEndTime;
    private Integer graceMinutes;
    private LocalTime absenceCutoffTime;
    private String timezone;
    private String shiftType;
}
