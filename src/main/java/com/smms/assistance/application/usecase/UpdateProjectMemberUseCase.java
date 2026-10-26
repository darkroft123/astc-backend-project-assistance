package com.smms.assistance.application.usecase;

import com.smms.assistance.application.in.ProjectPort;
import com.smms.assistance.domain.entity.ProjectMember;
import com.smms.assistance.infrastructure.messaging.OutboxEventPublisher;
import com.smms.assistance.shared.util.TimezoneService;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.UUID;

@ApplicationScoped
public class UpdateProjectMemberUseCase {

    private static final Logger LOG = Logger.getLogger(UpdateProjectMemberUseCase.class);

    private final ProjectPort projectPort;
    private final OutboxEventPublisher outboxPublisher;

    @Inject
    public UpdateProjectMemberUseCase(
            ProjectPort projectPort,
            OutboxEventPublisher outboxPublisher
    ){

        this.projectPort =
                projectPort;
        this.outboxPublisher = outboxPublisher;
    }

    public ProjectMember execute(

            UUID id,

            String role

    ){

        ProjectMember member =
                projectPort
                        .findMemberById(id)
                        .orElseThrow(
                                () ->
                                new RuntimeException(
                                        "Member not found"
                                )
                        );

        String previousRole = member.role;
        member.role =
                role;

        member.updatedAt = TimezoneService.utcNow();

        ProjectMember updated = projectPort
                .updateMember(member);

        LOG.infof("[NOTIFICATION] Project MEMBER_ROLE_CHANGED: memberId=%s, projectId=%s, userId=%s, previousRole=%s, newRole=%s",
                id, updated.projectId, updated.userId, previousRole, role);
        outboxPublisher.publish("PROJECT_MEMBER", id, "PROJECT_MEMBER_ROLE_CHANGED",
                "{\"memberId\":\"" + id + "\",\"projectId\":\"" + updated.projectId
                + "\",\"userId\":\"" + updated.userId + "\",\"previousRole\":\"" + escape(previousRole)
                + "\",\"newRole\":\"" + escape(role) + "\",\"role\":\"" + escape(role) + "\"}");

        return updated;
    }

    private String escape(String s) {
        return s != null ? s.replace("\\", "\\\\").replace("\"", "\\\"") : "";
    }
}