package com.smms.assistance.application.usecase;

import com.smms.assistance.application.in.ProjectPort;
import com.smms.assistance.domain.entity.Project;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
public class FindAllProjectsUseCase {

    private final ProjectPort projectPort;

    @Inject
    public FindAllProjectsUseCase(
            ProjectPort projectPort
    ) {

        this.projectPort = projectPort;
    }

    public List<Project> execute() {

        return projectPort
                .findAllProjects();
    }
}