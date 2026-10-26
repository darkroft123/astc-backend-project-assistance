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
public class JustificationHistoryOutput {
    private UUID id;
    private UUID justificationId;
    private String previousStatus;
    private String newStatus;
    private String comment;
    private UUID changedBy;
    private String changedAt;
}
