package com.smms.assistance.application.usecase;

import com.smms.assistance.application.in.ApproveAlertPort;
import com.smms.assistance.domain.entity.AlertEntity;
import com.smms.assistance.infrastructure.messaging.OutboxEventPublisher;
import com.smms.assistance.infrastructure.repository.AlertRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.util.UUID;

@ApplicationScoped
public class ApproveAlertUseCase implements ApproveAlertPort {

    private static final Logger LOG = Logger.getLogger(ApproveAlertUseCase.class);

    @Inject
    AlertRepository alertRepository;

    @Inject
    OutboxEventPublisher outboxPublisher;

    @Override
    @Transactional
    public AlertEntity execute(UUID alertId, UUID reviewedBy) {
        AlertEntity alert = alertRepository.findByIdOptional(alertId)
                .orElseThrow(() -> new IllegalArgumentException("Alert not found"));
        alert.setStatus("APPROVED");
        alertRepository.persist(alert);

        LOG.infof("[NOTIFICATION] Alert APPROVED: alertId=%s, userId=%s, reviewedBy=%s",
                alertId, alert.getUserId(), reviewedBy);
        outboxPublisher.publish("ALERT", alertId, "ALERT_APPROVED",
                "{\"alertId\":\"" + alertId + "\",\"userId\":\"" + alert.getUserId()
                + "\",\"projectId\":\"" + alert.getProjectId() + "\",\"reviewedBy\":\"" + reviewedBy + "\"}");

        return alert;
    }
}
