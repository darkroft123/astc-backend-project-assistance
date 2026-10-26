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
public class VacationBalanceOutput {
    private UUID id;
    private UUID userId;
    private Integer totalDays;
    private Integer usedDays;
    private Integer pendingDays;
    private Integer availableDays;
    private Integer year;
}
