package com.smms.assistance.infrastructure.repository;

import com.smms.assistance.domain.entity.OutboxEventEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class OutboxRepository implements PanacheRepositoryBase<OutboxEventEntity, UUID> {

    private static final Logger LOG = Logger.getLogger(OutboxRepository.class);

    @SuppressWarnings("unchecked")
    public List<OutboxEventEntity> findUnpublished(int limit) {
        List<OutboxEventEntity> results = getEntityManager().createNativeQuery(
                "SELECT * FROM outbox_events " +
                "WHERE published = false " +
                "ORDER BY created_at ASC " +
                "LIMIT :limit FOR UPDATE SKIP LOCKED", OutboxEventEntity.class)
                .setParameter("limit", limit)
                .getResultList();
        if (!results.isEmpty()) {
            LOG.infof("[OUTBOX] Found %d unpublished events with SKIP LOCKED", results.size());
        }
        return results;
    }

    public boolean existsUnpublished(String aggregateType, UUID aggregateId, String eventType) {
        return count("aggregateType = ?1 AND aggregateId = ?2 AND eventType = ?3 AND published = false",
                aggregateType, aggregateId, eventType) > 0;
    }

    public void persistNativeOnConflictDoNothing(OutboxEventEntity event) {
        getEntityManager().createNativeQuery(
                "INSERT INTO outbox_events (id, aggregate_type, aggregate_id, event_type, payload, status, published, created_at, attempts) " +
                "VALUES (:id, :aggregateType, :aggregateId, :eventType, cast(:payload as text), 'PENDING', false, now(), 0) " +
                "ON CONFLICT (event_type, aggregate_type, aggregate_id) WHERE published = false DO NOTHING")
                .setParameter("id", event.getId())
                .setParameter("aggregateType", event.getAggregateType())
                .setParameter("aggregateId", event.getAggregateId())
                .setParameter("eventType", event.getEventType())
                .setParameter("payload", event.getPayload())
                .executeUpdate();
    }
}
