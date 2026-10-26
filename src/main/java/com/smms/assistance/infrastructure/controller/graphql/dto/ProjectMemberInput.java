package com.smms.assistance.infrastructure.controller.graphql.dto;

import org.eclipse.microprofile.graphql.Input;

import java.util.UUID;

@Input("ProjectMemberInput")
public class ProjectMemberInput {

    public UUID projectId;

    public UUID userId;

    public String role;

}