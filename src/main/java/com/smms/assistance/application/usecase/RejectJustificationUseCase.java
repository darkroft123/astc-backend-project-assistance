package com.smms.assistance.application.usecase;

import com.smms.assistance.application.in.RejectJustificationPort;
import com.smms.assistance.domain.entity.JustificationEntity;
import com.smms.assistance.infrastructure.client.AssistanceClientWrapper;
import com.smms.assistance.infrastructure.messaging.OutboxEventPublisher;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.LocalDateTime;
import java.util.UUID;

@ApplicationScoped
public class RejectJustificationUseCase implements RejectJustificationPort {

    private static final Logger LOG = Logger.getLogger(RejectJustificationUseCase.class);

    @Inject
    AssistanceClientWrapper clientWrapper;

    @Inject
    OutboxEventPublisher outboxPublisher;

    @Override
    public JustificationEntity execute(UUID justificationId, String comment, UUID reviewedBy) {
        var dto = clientWrapper.getClient().rejectJustificationPM(justificationId, comment);

        if (dto == null) {
            throw new IllegalArgumentException("Justification not found or failed to reject");
        }

        JustificationEntity e = new JustificationEntity();
        e.setId(dto.getId());
        e.setAbsenceId(dto.getAbsenceId());
        e.setUserId(dto.getUserId());
        e.setDescription(dto.getDescription());
        e.setDocumentUrl(dto.getDocumentUrl());
        e.setStatus(dto.getStatus());
        e.setComment(dto.getComment());
        e.setSubmittedAt(dto.getSubmittedAt() != null ? LocalDateTime.parse(dto.getSubmittedAt()) : null);

        LOG.infof("[NOTIFICATION] Justification REJECTED: justificationId=%s, userId=%s, reviewedBy=%s",
                justificationId, dto.getUserId(), reviewedBy);
        outboxPublisher.publish("JUSTIFICATION", justificationId, "JUSTIFICATION_REJECTED",
                "{\"justificationId\":\"" + justificationId + "\",\"userId\":\"" + dto.getUserId()
                + "\",\"reviewedBy\":\"" + reviewedBy + "\",\"comment\":\"" + escape(comment) + "\"}");

        return e;
    }

    private String escape(String s) {
        return s != null ? s.replace("\\", "\\\\").replace("\"", "\\\"") : "";
    }
}

