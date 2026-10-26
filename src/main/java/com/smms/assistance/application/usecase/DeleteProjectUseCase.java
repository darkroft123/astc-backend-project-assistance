package com.smms.assistance.application.usecase;

import com.smms.assistance.application.in.ProjectPort;
import com.smms.assistance.infrastructure.messaging.OutboxEventPublisher;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.UUID;

@ApplicationScoped
public class DeleteProjectUseCase {

    private static final Logger LOG = Logger.getLogger(DeleteProjectUseCase.class);

    private final ProjectPort projectPort;
    private final OutboxEventPublisher outboxPublisher;

    @Inject
    public DeleteProjectUseCase(
            ProjectPort projectPort,
            OutboxEventPublisher outboxPublisher
    ) {
        this.projectPort = projectPort;
        this.outboxPublisher = outboxPublisher;
    }

    public boolean execute(
            UUID id
    ) {

        if (id == null) {
            throw new IllegalArgumentException(
                    "project id cannot be null"
            );
        }

        String recipientId = projectPort.findById(id)
                .map(p -> p.responsibleId)
                .map(uuid -> uuid.toString())
                .orElse(null);

        boolean deleted = projectPort.delete(id);
        if (deleted) {
            LOG.infof("[NOTIFICATION] Project DELETED: projectId=%s", id);
            outboxPublisher.publish("PROJECT", id, "PROJECT_DELETED",
                    "{\"projectId\":\"" + id + "\",\"userId\":\"" + recipientId + "\"}");
        }
        return deleted;
    }
}