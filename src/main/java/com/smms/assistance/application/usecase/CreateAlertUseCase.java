package com.smms.assistance.application.usecase;

import com.smms.assistance.application.in.ProjectPort;
import com.smms.assistance.domain.entity.AlertEntity;
import com.smms.assistance.infrastructure.messaging.OutboxEventPublisher;
import com.smms.assistance.infrastructure.repository.AlertRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;
import java.util.UUID;

@ApplicationScoped
public class CreateAlertUseCase {

    private static final Logger LOG = Logger.getLogger(CreateAlertUseCase.class);

    @Inject
    AlertRepository alertRepository;

    @Inject
    OutboxEventPublisher outboxPublisher;

    @Inject
    ProjectPort projectPort;

    @Transactional
    public AlertEntity execute(UUID userId, UUID projectId, String type, String detail, Double latitude, Double longitude) {
        AlertEntity alert = AlertEntity.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .projectId(projectId)
                .type(type)
                .status("PENDING")
                .detail(detail)
                .latitude(latitude)
                .longitude(longitude)
                .build();
        alertRepository.persist(alert);

        String recipientId = projectPort.findById(projectId)
                .map(p -> p.responsibleId)
                .filter(id -> id != null)
                .map(UUID::toString)
                .orElse(userId.toString());

        LOG.infof("[NOTIFICATION] Alert CREATED: alertId=%s, userId=%s, projectId=%s, type=%s, notified=%s",
                alert.getId(), userId, projectId, type, recipientId);
        outboxPublisher.publish("ALERT", alert.getId(), "ALERT_CREATED",
                "{\"alertId\":\"" + alert.getId() + "\",\"userId\":\"" + userId
                + "\",\"projectId\":\"" + projectId + "\",\"recipientUserId\":\"" + recipientId
                + "\",\"type\":\"" + escape(type) + "\"}");

        return alert;
    }

    private String escape(String s) {
        return s != null ? s.replace("\\", "\\\\").replace("\"", "\\\"") : "";
    }
}
