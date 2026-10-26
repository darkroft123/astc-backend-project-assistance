package com.smms.assistance.application.in;

import com.smms.assistance.domain.entity.*;

import java.util.*;

public interface ProjectPort {

    Project create(Project project);

    // void addMember(ProjectMember member);

    ProjectMember addMember(
        ProjectMember member
);

    Optional<Project> findById(UUID id);

    List<Project> findAllProjects();

    Project update(Project project);

    boolean delete(UUID id);

    List<ProjectMember> findMembersByProjectId(
        UUID projectId
    );

    ProjectMember updateMember(
            ProjectMember member
    );

    boolean deleteMember(
            UUID id
    );

    int deleteMembersByUserId(
            UUID userId
    );

    Optional<ProjectMember> findMemberById(
            UUID id
    );

}