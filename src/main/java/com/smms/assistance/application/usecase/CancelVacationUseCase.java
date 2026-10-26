package com.smms.assistance.application.usecase;

import com.smms.assistance.application.in.CancelVacationPort;
import com.smms.assistance.domain.entity.VacationBalanceEntity;
import com.smms.assistance.domain.entity.VacationRequestEntity;
import com.smms.assistance.infrastructure.messaging.OutboxEventPublisher;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class CancelVacationUseCase implements CancelVacationPort {

    private static final Logger LOG = Logger.getLogger(CancelVacationUseCase.class);

    @Inject
    OutboxEventPublisher outboxPublisher;

    @Override
    @Transactional
    public VacationRequestEntity execute(UUID requestId, UUID userId) {
        Optional<VacationRequestEntity> opt = VacationRequestEntity.findByIdOptional(requestId);
        if (opt.isEmpty()) {
            throw new IllegalArgumentException("Solicitud de vacaciones no encontrada: " + requestId);
        }

        VacationRequestEntity entity = opt.get();
        if (!entity.getUserId().equals(userId)) {
            throw new SecurityException("No puedes cancelar una solicitud que no te pertenece");
        }
        if (!"PENDING".equals(entity.getStatus())) {
            throw new IllegalStateException("No se puede cancelar una solicitud " + entity.getStatus());
        }

        entity.setStatus("CANCELLED");
        entity.persist();

        Optional<VacationBalanceEntity> balance = VacationBalanceEntity
                .find("userId = ?1 and year = ?2", userId, entity.getStartDate().getYear())
                .firstResultOptional();

        balance.ifPresent(bal -> {
            bal.setPendingDays(bal.getPendingDays() - entity.getBusinessDays());
            bal.persist();
        });

        LOG.infof("[VACATION] Cancelled: requestId=%s, userId=%s", requestId, userId);
        outboxPublisher.publish("VACATION", entity.getId(), "VACATION_CANCELLED",
                "{\"userId\":\"" + entity.getUserId() + "\",\"startDate\":\"" + entity.getStartDate()
                + "\",\"endDate\":\"" + entity.getEndDate() + "\",\"businessDays\":" + entity.getBusinessDays() + "}");

        return entity;
    }
}
