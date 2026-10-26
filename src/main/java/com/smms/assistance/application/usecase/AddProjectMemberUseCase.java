package com.smms.assistance.application.usecase;

import com.smms.assistance.application.in.ProjectPort;
import com.smms.assistance.domain.entity.ProjectMember;
import com.smms.assistance.infrastructure.controller.graphql.dto.ProjectMemberInput;
import com.smms.assistance.infrastructure.messaging.OutboxEventPublisher;
import com.smms.assistance.shared.util.TimezoneService;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.UUID;

@ApplicationScoped
public class AddProjectMemberUseCase {

    private static final Logger LOG = Logger.getLogger(AddProjectMemberUseCase.class);

    private final ProjectPort projectPort;
    private final OutboxEventPublisher outboxPublisher;

    @Inject
    public AddProjectMemberUseCase(
            ProjectPort projectPort,
            OutboxEventPublisher outboxPublisher
    ){

        this.projectPort =
                projectPort;
        this.outboxPublisher = outboxPublisher;
    }

    public ProjectMember execute(
            ProjectMemberInput input
    ){

        ProjectMember member =
                new ProjectMember();

        member.id =
                UUID.randomUUID();

        member.projectId =
                input.projectId;

        member.userId =
                input.userId;

        member.role =
                input.role;

        member.createdAt = TimezoneService.utcNow();
        member.updatedAt = TimezoneService.utcNow();

        ProjectMember saved = projectPort
                .addMember(member);

        LOG.infof("[NOTIFICATION] Project MEMBER_ADDED: memberId=%s, projectId=%s, userId=%s, role=%s",
                saved.id, input.projectId, input.userId, input.role);
        outboxPublisher.publish("PROJECT_MEMBER", saved.id, "PROJECT_MEMBER_ADDED",
                "{\"memberId\":\"" + saved.id + "\",\"projectId\":\"" + input.projectId
                + "\",\"userId\":\"" + input.userId + "\",\"role\":\"" + escape(input.role) + "\"}");

        return saved;
    }

    private String escape(String s) {
        return s != null ? s.replace("\\", "\\\\").replace("\"", "\\\"") : "";
    }
}