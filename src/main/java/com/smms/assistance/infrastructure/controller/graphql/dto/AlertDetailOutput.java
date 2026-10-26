package com.smms.assistance.infrastructure.controller.graphql.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.graphql.Type;

import java.time.LocalDateTime;
import java.util.UUID;

@Type
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertDetailOutput {
    private UUID id;
    private String type;
    private String status;
    private String detail;
    private Double latitude;
    private Double longitude;
    private LocalDateTime createdAt;
    private UUID userId;
    private UUID projectId;
    private String severity;

    public String getMessage() {
        return detail;
    }
}