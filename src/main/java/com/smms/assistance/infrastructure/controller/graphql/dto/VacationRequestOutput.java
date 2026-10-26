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
public class VacationRequestOutput {
    private UUID id;
    private UUID userId;
    private UUID projectId;
    private String startDate;
    private String endDate;
    private Integer businessDays;
    private String status;
    private String comment;
    private String reviewedBy;
    private String reviewedAt;
    private String createdAt;
}
