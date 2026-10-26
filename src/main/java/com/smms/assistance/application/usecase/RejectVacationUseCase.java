package com.smms.assistance.application.usecase;

import com.smms.assistance.application.in.RejectVacationPort;
import com.smms.assistance.domain.entity.VacationBalanceEntity;
import com.smms.assistance.domain.entity.VacationRequestEntity;
import com.smms.assistance.infrastructure.messaging.OutboxEventPublisher;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class RejectVacationUseCase implements RejectVacationPort {

    private static final Logger LOG = Logger.getLogger(RejectVacationUseCase.class);

    @Inject
    OutboxEventPublisher outboxPublisher;

    @Override
    @Transactional
    public VacationRequestEntity execute(UUID requestId, UUID reviewerId, String comment) {
        Optional<VacationRequestEntity> opt = VacationRequestEntity.findByIdOptional(requestId);
        if (opt.isEmpty()) {
            throw new IllegalArgumentException("Solicitud de vacaciones no encontrada: " + requestId);
        }

        VacationRequestEntity entity = opt.get();
        if (!"PENDING".equals(entity.getStatus())) {
            throw new IllegalStateException("La solicitud ya fue " + entity.getStatus());
        }

        LocalDateTime utcNow = Instant.now().atZone(ZoneOffset.UTC).toLocalDateTime();
        entity.setStatus("REJECTED");
        entity.setReviewedBy(reviewerId);
        entity.setReviewedAt(utcNow);
        entity.setComment(comment);
        entity.persist();

        Optional<VacationBalanceEntity> balance = VacationBalanceEntity
                .find("userId = ?1 and year = ?2", entity.getUserId(), entity.getStartDate().getYear())
                .firstResultOptional();

        balance.ifPresent(bal -> {
            bal.setPendingDays(bal.getPendingDays() - entity.getBusinessDays());
            bal.persist();
        });

        LOG.infof("[VACATION] Rejected: requestId=%s, userId=%s, reviewerId=%s", requestId, entity.getUserId(), reviewerId);
        outboxPublisher.publish("VACATION", entity.getId(), "VACATION_REJECTED",
                "{\"userId\":\"" + entity.getUserId() + "\",\"startDate\":\"" + entity.getStartDate()
                + "\",\"endDate\":\"" + entity.getEndDate() + "\",\"reason\":\"" + (comment != null ? comment : "") + "\"}");

        return entity;
    }
}
