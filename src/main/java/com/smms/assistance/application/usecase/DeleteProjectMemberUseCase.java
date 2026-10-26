package com.smms.assistance.application.usecase;

import com.smms.assistance.application.in.ProjectPort;
import com.smms.assistance.infrastructure.messaging.OutboxEventPublisher;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.UUID;

@ApplicationScoped
public class DeleteProjectMemberUseCase {

    private static final Logger LOG = Logger.getLogger(DeleteProjectMemberUseCase.class);

    private final ProjectPort projectPort;
    private final OutboxEventPublisher outboxPublisher;

    @Inject
    public DeleteProjectMemberUseCase(
            ProjectPort projectPort,
            OutboxEventPublisher outboxPublisher
    ){

        this.projectPort =
                projectPort;
        this.outboxPublisher = outboxPublisher;
    }

    public boolean execute(
            UUID id
    ){

        String recipientId = projectPort.findMemberById(id)
                .map(m -> m.userId)
                .map(uuid -> uuid.toString())
                .orElse(null);

        boolean deleted = projectPort
                .deleteMember(id);
        if (deleted) {
            LOG.infof("[NOTIFICATION] Project MEMBER_REMOVED: memberId=%s", id);
            outboxPublisher.publish("PROJECT_MEMBER", id, "PROJECT_MEMBER_REMOVED",
                    "{\"memberId\":\"" + id + "\",\"userId\":\"" + recipientId + "\"}");
        }
        return deleted;
    }
}