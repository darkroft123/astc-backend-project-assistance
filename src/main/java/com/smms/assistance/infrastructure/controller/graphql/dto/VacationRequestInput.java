package com.smms.assistance.infrastructure.controller.graphql.dto;

import org.eclipse.microprofile.graphql.Input;

import java.time.LocalDate;
import java.util.UUID;

@Input("VacationRequestInput")
public class VacationRequestInput {
    public UUID userId;
    public UUID projectId;
    public LocalDate startDate;
    public LocalDate endDate;
    public Integer businessDays;
    public String comment;
}
