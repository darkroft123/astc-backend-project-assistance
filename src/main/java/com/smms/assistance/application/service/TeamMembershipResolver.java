package com.smms.assistance.application.service;

import com.smms.assistance.domain.entity.Project;
import com.smms.assistance.domain.entity.ProjectMember;
import com.smms.assistance.infrastructure.security.AuthContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Centraliza la resolución de ids de usuario del equipo de un PM (antes duplicada como getFilteredUserIds en varios use
 * cases). Utiliza únicamente queries Panache parametrizadas.
 */
@ApplicationScoped
public class TeamMembershipResolver {

    @Inject
    AuthContext authContext;

    public List<UUID> resolve(UUID projectId, UUID userId, UUID currentPmId) {
        boolean isAdmin = authContext != null && authContext.isAdmin();

        if (userId != null) {
            if (projectId != null) {
                if (isAdmin) return List.of(userId);
                boolean isMember = ProjectMember.count("projectId = ?1 and userId = ?2", projectId, userId) > 0;
                return isMember ? List.of(userId) : List.of();
            } else {
                if (isAdmin) return List.of(userId);
                List<UUID> pmProjectIds = projectIdsOf(currentPmId);
                if (pmProjectIds.isEmpty()) return List.of();
                boolean isMemberOfAny = ProjectMember.count("projectId in ?1 and userId = ?2", pmProjectIds, userId) > 0;
                return isMemberOfAny ? List.of(userId) : List.of();
            }
        } else {
            if (projectId != null) {
                List<ProjectMember> members = ProjectMember.list("projectId = ?1", projectId);
                return members.stream().map(m -> m.userId).collect(Collectors.toList());
            } else {
                if (isAdmin) {
                    List<ProjectMember> allMembers = ProjectMember.listAll();
                    List<UUID> allMemberIds = allMembers.stream().map(m -> m.userId).distinct().collect(Collectors.toList());
                    return allMemberIds;
                }
                List<UUID> pmProjectIds = projectIdsOf(currentPmId);
                if (pmProjectIds.isEmpty()) return List.of();
                List<ProjectMember> allMembers = ProjectMember.list("projectId in ?1", pmProjectIds);
                return allMembers.stream().map(m -> m.userId).distinct().collect(Collectors.toList());
            }
        }
    }

    /**
     * Resuelve los miembros gestionados por el PM (sin filtro por proyecto). En caso de no encontrar ninguno devuelve la
     * lista vacía o, si includeOwner=true, el propio PM (para que pueda consultar sus propias justificaciones).
     */
    public List<UUID> resolveManagedByPm(UUID userId, UUID currentPmId, boolean includeOwner) {
        boolean isAdmin = authContext != null && authContext.isAdmin();

        if (userId != null) {
            if (isAdmin) return List.of(userId);
            List<UUID> pmProjectIds = projectIdsOf(currentPmId);
            if (pmProjectIds.isEmpty()) return includeOwner ? List.of(currentPmId) : List.of();
            boolean isMemberOfAny = ProjectMember.count("projectId in ?1 and userId = ?2", pmProjectIds, userId) > 0;
            if (isMemberOfAny) {
                return List.of(userId);
            }
            return includeOwner ? List.of(currentPmId) : List.of();
        } else {
            if (isAdmin) {
                List<ProjectMember> allMembers = ProjectMember.listAll();
                List<UUID> memberIds = allMembers.stream().map(m -> m.userId).distinct().collect(Collectors.toList());
                if (includeOwner && !memberIds.contains(currentPmId)) {
                    memberIds = new java.util.ArrayList<>(memberIds);
                    memberIds.add(currentPmId);
                }
                return memberIds;
            }
            List<UUID> pmProjectIds = projectIdsOf(currentPmId);
            if (pmProjectIds.isEmpty()) return includeOwner ? List.of(currentPmId) : List.of();
            List<ProjectMember> allMembers = ProjectMember.list("projectId in ?1", pmProjectIds);
            List<UUID> memberIds = allMembers.stream().map(m -> m.userId).distinct().collect(Collectors.toList());
            if (includeOwner && !memberIds.contains(currentPmId)) {
                memberIds = new java.util.ArrayList<>(memberIds);
                memberIds.add(currentPmId);
            }
            return memberIds;
        }
    }

    private List<UUID> projectIdsOf(UUID pmId) {
        List<ProjectMember> memberships = ProjectMember.list("userId = ?1", pmId);
        java.util.Set<UUID> projectIds = memberships.stream()
                .map(m -> m.projectId)
                .collect(Collectors.toSet());
        List<Project> responsibleProjects = Project.list("responsibleId = ?1 and deletedAt is null", pmId);
        for (Project p : responsibleProjects) {
            if (p.id != null) {
                projectIds.add(p.id);
            }
        }
        return new java.util.ArrayList<>(projectIds);
    }
}