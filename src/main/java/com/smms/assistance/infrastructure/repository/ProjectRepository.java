package com.smms.assistance.infrastructure.repository;

import com.smms.assistance.application.in.ProjectPort;
import com.smms.assistance.domain.entity.Project;
import com.smms.assistance.domain.entity.ProjectMember;
import com.smms.assistance.shared.util.TimezoneService;

import io.quarkus.hibernate.orm.panache.PanacheRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.*;

@ApplicationScoped
public class ProjectRepository
        implements PanacheRepository<Project>,
        ProjectPort {

    // ==================== CREATE ====================

    @Override
    @Transactional
    public Project create(Project project) {

        persist(project);

        return project;
    }

    // ==================== PROJECT MEMBERS ====================

    @Override
@Transactional
public ProjectMember addMember(
        ProjectMember member
){

    getEntityManager()
            .persist(member);

    return member;
}

    // ==================== QUERIES ====================

    @Override
    public Optional<Project> findById(
            UUID id
    ) {

        return find(
                "id = ?1 and deletedAt is null",
                id
        ).firstResultOptional();
    }

    @Override
    public List<Project> findAllProjects() {

        return list(
                "deletedAt is null"
        );
    }

    // ==================== UPDATE ====================

    @Override
    @Transactional
    public Project update(Project project) {

        project.updatedAt =
                TimezoneService.utcNow();

        getEntityManager()
                .merge(project);

        return project;
    }

    // ==================== SOFT DELETE ====================

    @Override
    @Transactional
    public boolean delete(
            UUID id
    ) {

        Optional<Project> optionalProject =
                findById(id);

        if(optionalProject.isEmpty()) {

            return false;
        }

        Project project =
                optionalProject.get();

        project.deletedAt =
                TimezoneService.utcNow();

        project.updatedAt =
                TimezoneService.utcNow();

        getEntityManager()

                .createQuery(
                        """
                        DELETE FROM ProjectMember
                        WHERE projectId=:projectId
                        """
                )

                .setParameter(
                        "projectId",
                        id
                )

                .executeUpdate();

        return true;
    }

    @Override
public List<ProjectMember>
findMembersByProjectId(
        UUID projectId
){

    return getEntityManager()

            .createQuery(
                """
                SELECT pm
                FROM ProjectMember pm
                WHERE pm.projectId=:projectId
                """,
                ProjectMember.class
            )

            .setParameter(
                "projectId",
                projectId
            )

            .getResultList();
}

@Override
public Optional<ProjectMember>
findMemberById(
        UUID id
){

    ProjectMember member =
            getEntityManager()
                    .find(
                        ProjectMember.class,
                        id
                    );

    return Optional.ofNullable(
            member
    );
}

@Override
@Transactional
public ProjectMember updateMember(
        ProjectMember member
){

    return getEntityManager()
            .merge(member);
}

@Override
@Transactional
public boolean deleteMember(
        UUID id
){

    return getEntityManager()

            .createQuery(
                    """
                    DELETE FROM ProjectMember
                    WHERE id=:id
                    """
            )

            .setParameter(
                    "id",
                    id
            )

            .executeUpdate()

            >0;
}

@Override
@Transactional
public int deleteMembersByUserId(
        UUID userId
){

    return getEntityManager()

            .createQuery(
                    """
                    DELETE FROM ProjectMember
                    WHERE userId=:userId
                    """
            )

            .setParameter(
                    "userId",
                    userId
            )

            .executeUpdate();
}


}