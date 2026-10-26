package com.smms.assistance.infrastructure.controller.graphql.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public class ProjectMemberOutput {

    public UUID id;

    public UUID projectId;

    public UUID userId;

    public Boolean hasCustomSchedule;

    public String role;

    public LocalDateTime createdAt;
}