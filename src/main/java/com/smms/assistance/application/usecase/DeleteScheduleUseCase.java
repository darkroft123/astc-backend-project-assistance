package com.smms.assistance.application.usecase;

import com.smms.assistance.application.in.DeleteSchedulePort;
import com.smms.assistance.domain.entity.ScheduleEntity;
import com.smms.assistance.infrastructure.messaging.OutboxEventPublisher;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.util.UUID;

@ApplicationScoped
public class DeleteScheduleUseCase implements DeleteSchedulePort {

    private static final Logger LOG = Logger.getLogger(DeleteScheduleUseCase.class);

    @Inject
    OutboxEventPublisher outboxPublisher;

    @Override
    @Transactional
    public boolean execute(UUID userId, UUID projectId) {
        long deleted = ScheduleEntity.delete("userId = ?1 and projectId = ?2", userId, projectId);
        if (deleted > 0) {
            LOG.infof("[NOTIFICATION] Schedule DELETED: userId=%s, projectId=%s", userId, projectId);
            outboxPublisher.publish("SCHEDULE", UUID.randomUUID(), "SCHEDULE_DELETED",
                    "{\"userId\":\"" + userId + "\",\"projectId\":\"" + projectId + "\"}");
        }
        return deleted > 0;
    }
}
