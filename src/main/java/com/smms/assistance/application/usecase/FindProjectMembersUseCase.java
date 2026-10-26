package com.smms.assistance.application.usecase;

import com.smms.assistance.application.in.ProjectPort;
import com.smms.assistance.domain.entity.ProjectMember;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class FindProjectMembersUseCase {

    private final ProjectPort projectPort;

    @Inject
    public FindProjectMembersUseCase(
            ProjectPort projectPort
    ){

        this.projectPort =
                projectPort;
    }

    public List<ProjectMember> execute(
            UUID projectId
    ){

        return projectPort
                .findMembersByProjectId(
                        projectId
                );
    }
}