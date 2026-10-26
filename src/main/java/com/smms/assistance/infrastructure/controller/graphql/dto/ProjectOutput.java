package com.smms.assistance.infrastructure.controller.graphql.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

public class ProjectOutput {

    public UUID id;

    public String name;
    public String description;
    public String status;

    public LocalDate startDate;
    public LocalDate endDate;

    public BigDecimal budget;
    public String currency;

   
    public String workStartTime;
    public String workEndTime;
    public Integer graceMinutes;
    public String shiftType;
    public String timezone;

    public UUID responsibleId;

    public Double latitude;
    public Double longitude;
    public Double radius;

    public LocalDateTime createdAt;
    public String absenceCutoffTime;
    public java.util.List<ProjectMemberOutput> members;

    public Integer vacationEligibilityDays;
    public java.util.List<String> holidays;

    public ProjectOutput() {}
}