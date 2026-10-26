package com.smms.assistance.application.usecase;

import com.smms.assistance.application.in.HasApprovedVacationPort;
import com.smms.assistance.domain.entity.VacationRequestEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class HasApprovedVacationUseCase implements HasApprovedVacationPort {

    @Override
    @Transactional
    public Optional<Object[]> execute(UUID userId, LocalDate date) {
        return VacationRequestEntity.getEntityManager()
                .createQuery("select v.id, v.businessDays from VacationRequestEntity v where v.userId = ?1 and v.status = 'APPROVED' and v.startDate <= ?2 and v.endDate >= ?2", Object[].class)
                .setParameter(1, userId)
                .setParameter(2, date)
                .getResultStream()
                .findFirst();
    }
}
