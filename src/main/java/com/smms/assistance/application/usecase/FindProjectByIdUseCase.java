package com.smms.assistance.application.usecase;

import com.smms.assistance.application.in.ProjectPort;
import com.smms.assistance.domain.entity.Project;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.UUID;

@ApplicationScoped
public class FindProjectByIdUseCase {

    private final ProjectPort projectPort;

    @Inject
    public FindProjectByIdUseCase(
            ProjectPort projectPort
    ) {
        this.projectPort = projectPort;
    }

    public Project execute(
            UUID id
    ) {

        if (id == null) {
            throw new IllegalArgumentException(
                    "project id cannot be null"
            );
        }

        return projectPort
                .findById(id)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Project not found"
                        ));
    }
}