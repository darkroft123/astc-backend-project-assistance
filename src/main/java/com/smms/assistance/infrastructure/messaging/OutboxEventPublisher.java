package com.smms.assistance.infrastructure.messaging;

import com.smms.assistance.domain.entity.OutboxEventEntity;
import com.smms.assistance.infrastructure.repository.OutboxRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;
import java.util.UUID;

/**
 * Writes domain events to the outbox table within the same transaction.
 * A separate scheduler/poller will read and publish them to RabbitMQ.
 */
@ApplicationScoped
public class OutboxEventPublisher {

    private static final Logger LOG = Logger.getLogger(OutboxEventPublisher.class);

    @Inject
    OutboxRepository outboxRepository;

    @Transactional
    public void publish(String aggregateType, UUID aggregateId, String eventType, String payload) {
        if (outboxRepository.existsUnpublished(aggregateType, aggregateId, eventType)) {
            LOG.infof("[OUTBOX] Skipped duplicate unpublished event: type=%s, aggregateType=%s, aggregateId=%s",
                    eventType, aggregateType, aggregateId);
            return;
        }
        OutboxEventEntity event = OutboxEventEntity.builder()
                .id(UUID.randomUUID())
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .eventType(eventType)
                .payload(payload)
                .published(false)
                .build();
        outboxRepository.persistNativeOnConflictDoNothing(event);
        LOG.infof("[OUTBOX] Event persisted (or ignored on conflict): id=%s, type=%s, aggregateType=%s, aggregateId=%s",
                event.getId(), eventType, aggregateType, aggregateId);
    }
}
