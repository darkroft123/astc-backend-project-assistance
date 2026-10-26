package com.smms.assistance.infrastructure.controller.graphql.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class HolidayOutput {
    private UUID id;
    private String type;
    private UUID targetId;
    private LocalDate date;
    private String name;
}
