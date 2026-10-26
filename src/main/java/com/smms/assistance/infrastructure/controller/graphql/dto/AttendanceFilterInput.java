package com.smms.assistance.infrastructure.controller.graphql.dto;

import lombok.*;
import java.util.UUID;
import org.eclipse.microprofile.graphql.Input;
@Input
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceFilterInput {
    @Builder.Default
    private int page = 0;
    @Builder.Default
    private int size = 20;
    private UUID projectId;
    private String fromDate;
    private String toDate;
    private String status;
}