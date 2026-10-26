package com.smms.assistance.infrastructure.controller.graphql.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ImportHolidaysResultOutput {
    private int total;
    private int created;
    private int skipped;
    private int errors;
    private List<HolidayOutput> holidays;
}
