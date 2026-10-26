package com.smms.assistance.application.usecase;

import com.smms.assistance.application.in.RequestObservationPort;
import com.smms.assistance.domain.entity.JustificationEntity;
import com.smms.assistance.infrastructure.client.AssistanceClientWrapper;
import com.smms.assistance.infrastructure.messaging.OutboxEventPublisher;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.LocalDateTime;
import java.util.UUID;

@ApplicationScoped
public class RequestObservationUseCase implements RequestObservationPort {

    private static final Logger LOG = Logger.getLogger(RequestObservationUseCase.class);

    @Inject
    AssistanceClientWrapper clientWrapper;

    @Inject
    OutboxEventPublisher outboxPublisher;

    @Override
    public JustificationEntity execute(UUID justificationId, String comment, UUID reviewedBy) {
        if (comment == null || comment.isBlank()) {
            throw new IllegalArgumentException("Observation comment is required");
        }

        var detailDto = clientWrapper.getClient().getJustificationDetailPM(justificationId);
        String previousStatus = detailDto != null ? detailDto.getStatus() : "UNKNOWN";

        if (!"SUBMITTED".equals(previousStatus) && !"PENDING".equals(previousStatus)) {
            throw new IllegalStateException("Can only request observation for justifications in PENDING or SUBMITTED status. Current: " + previousStatus);
        }

        var dto = clientWrapper.getClient().requestObservationPM(justificationId, comment);

        if (dto == null) {
            throw new IllegalArgumentException("Justification not found or failed to request observation");
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

        LOG.infof("[NOTIFICATION] Justification OBSERVED: justificationId=%s, userId=%s, reviewedBy=%s",
                justificationId, dto.getUserId(), reviewedBy);
        outboxPublisher.publish("JUSTIFICATION", justificationId, "JUSTIFICATION_OBSERVED",
                "{\"justificationId\":\"" + justificationId + "\",\"userId\":\"" + dto.getUserId()
                + "\",\"reviewedBy\":\"" + reviewedBy + "\",\"comment\":\"" + escape(comment) + "\"}");

        return e;
    }

    private String escape(String s) {
        return s != null ? s.replace("\\", "\\\\").replace("\"", "\\\"") : "";
    }
}
