package com.smms.assistance.application.usecase;

import com.smms.assistance.application.in.ApproveVacationPort;
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
public class ApproveVacationUseCase implements ApproveVacationPort {

    private static final Logger LOG = Logger.getLogger(ApproveVacationUseCase.class);

    @Inject
    OutboxEventPublisher outboxPublisher;

    @Override
    @Transactional
    public VacationRequestEntity execute(UUID requestId, UUID reviewerId, Integer discountDays) {
        Optional<VacationRequestEntity> opt = VacationRequestEntity.findByIdOptional(requestId);
        if (opt.isEmpty()) {
            throw new IllegalArgumentException("Solicitud de vacaciones no encontrada: " + requestId);
        }

        VacationRequestEntity entity = opt.get();
        if (!"PENDING".equals(entity.getStatus())) {
            throw new IllegalStateException("La solicitud ya fue " + entity.getStatus());
        }

        LocalDateTime utcNow = Instant.now().atZone(ZoneOffset.UTC).toLocalDateTime();
        entity.setStatus("APPROVED");
        entity.setReviewedBy(reviewerId);
        entity.setReviewedAt(utcNow);
        entity.persist();

        Optional<VacationBalanceEntity> balance = VacationBalanceEntity
                .find("userId = ?1 and year = ?2", entity.getUserId(), entity.getStartDate().getYear())
                .firstResultOptional();

        balance.ifPresent(bal -> {
            bal.setUsedDays(bal.getUsedDays() + entity.getBusinessDays());
            bal.setPendingDays(bal.getPendingDays() - entity.getBusinessDays());
            
            if (discountDays != null && discountDays > 0) {
                // If the user has unjustified absences, we reduce their total available vacation days
                // and accordingly their pending days as a penalty/discount.
                bal.setTotalDays(Math.max(0, bal.getTotalDays() - discountDays));
                bal.setPendingDays(Math.max(0, bal.getPendingDays() - discountDays));
            }
            
            bal.persist();
        });

        LOG.infof("[VACATION] Approved: requestId=%s, userId=%s, reviewerId=%s, discountDays=%d", requestId, entity.getUserId(), reviewerId, discountDays != null ? discountDays : 0);
        outboxPublisher.publish("VACATION", entity.getId(), "VACATION_APPROVED",
                "{\"userId\":\"" + entity.getUserId() + "\",\"startDate\":\"" + entity.getStartDate()
                + "\",\"endDate\":\"" + entity.getEndDate() + "\",\"businessDays\":" + entity.getBusinessDays() + "}");

        return entity;
    }
}
