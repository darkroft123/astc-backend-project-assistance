package com.smms.assistance.application.usecase;

import com.smms.assistance.application.in.RequestVacationPort;
import com.smms.assistance.domain.entity.VacationBalanceEntity;
import com.smms.assistance.domain.entity.VacationRequestEntity;
import com.smms.assistance.infrastructure.repository.HolidayRepository;
import com.smms.assistance.infrastructure.messaging.OutboxEventPublisher;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class RequestVacationUseCase implements RequestVacationPort {

    private static final Logger LOG = Logger.getLogger(RequestVacationUseCase.class);

    @Inject
    OutboxEventPublisher outboxPublisher;

    @Inject
    HolidayRepository holidayRepository;

    @Override
    @Transactional
    public VacationRequestEntity execute(UUID userId, UUID projectId, LocalDate startDate,
                                          LocalDate endDate, Integer businessDays, String comment) {
        if (projectId == null) {
            throw new IllegalStateException("Debe especificar un proyecto para solicitar vacaciones.");
        }

        com.smms.assistance.domain.entity.Project project = com.smms.assistance.domain.entity.Project.findById(projectId);
        if (project == null) {
            throw new IllegalStateException("El proyecto especificado no existe.");
        }

        com.smms.assistance.domain.entity.ProjectMember pm = com.smms.assistance.domain.entity.ProjectMember
                .find("projectId = ?1 and userId = ?2", projectId, userId).firstResult();
        boolean isResponsible = project.responsibleId != null && project.responsibleId.equals(userId);
        if (pm == null && !isResponsible) {
            throw new IllegalStateException("No eres miembro del proyecto seleccionado.");
        }

        LocalDate membershipDate = (pm != null && pm.createdAt != null)
                ? pm.createdAt.toLocalDate()
                : (project.createdAt != null ? project.createdAt.toLocalDate() : LocalDate.now().minusDays(90));

        long daysElapsed = java.time.temporal.ChronoUnit.DAYS.between(membershipDate, LocalDate.now());
        int requiredDays = project.vacationEligibilityDays != null ? project.vacationEligibilityDays : 0;
        if (requiredDays > 0 && daysElapsed < requiredDays && !isResponsible) {
            throw new IllegalStateException("No cuenta con los días mínimos requeridos en el proyecto para solicitar vacaciones (" + requiredDays + " días). Han pasado " + daysElapsed + " días.");
        }

        Optional<VacationBalanceEntity> balance = VacationBalanceEntity
                .find("userId = ?1 and year = ?2", userId, LocalDate.now().getYear())
                .firstResultOptional();

        VacationBalanceEntity bal;
        if (balance.isEmpty()) {
            String defaultDaysStr = "30";
            try {
                Object result = io.quarkus.hibernate.orm.panache.PanacheEntityBase
                        .getEntityManager()
                        .createNativeQuery("select value from admin_settings where key = 'default_vacation_days'")
                        .getSingleResult();
                if (result != null) {
                    defaultDaysStr = result.toString();
                }
            } catch (Exception e) {
                // Use default value
            }
            int defaultDays = Integer.parseInt(defaultDaysStr);
            bal = new VacationBalanceEntity();
            bal.setId(UUID.randomUUID());
            bal.setUserId(userId);
            bal.setYear(LocalDate.now().getYear());
            bal.setTotalDays(defaultDays);
            bal.setUsedDays(0);
            bal.setPendingDays(0);
            bal.setCreatedAt(java.time.LocalDateTime.now());
            bal.setUpdatedAt(java.time.LocalDateTime.now());
            bal.persist();
        } else {
            bal = balance.get();
        }

        // Precompute holidays in range to avoid N+1 (1 query de holidays + 1 query de items de coleccion)
        List<com.smms.assistance.domain.entity.HolidayEntity> relevant =
                holidayRepository.findRelevantInRange(userId, projectId, startDate, endDate);

        Set<LocalDate> userExcludeDates = new HashSet<>();
        Set<LocalDate> userIncludeDates = new HashSet<>();
        Set<LocalDate> projectGlobalDates = new HashSet<>();
        for (com.smms.assistance.domain.entity.HolidayEntity h : relevant) {
            String type = h.getType();
            if ("USER_EXCLUDE".equals(type)) {
                userExcludeDates.add(h.getDate());
            } else if ("USER_INCLUDE".equals(type)) {
                userIncludeDates.add(h.getDate());
            } else if ("PROJECT".equals(type) || "GLOBAL".equals(type)) {
                projectGlobalDates.add(h.getDate());
            }
        }

        List<LocalDate> collectionDates =
                holidayRepository.findCollectionHolidayDatesInRange(projectId, startDate, endDate);
        Set<LocalDate> collectionDateSet = new HashSet<>(collectionDates);

        // Calculate days server-side, excluding project holidays (weekends count as vacation)
        int calculatedDays = 0;
        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            // Misma precedencia que HolidayRepository.isHolidayForUser
            boolean isExclude = userExcludeDates.contains(current);
            boolean isHoliday;
            if (isExclude) {
                isHoliday = false;
            } else if (userIncludeDates.contains(current)) {
                isHoliday = true;
            } else if (projectGlobalDates.contains(current)) {
                isHoliday = true;
            } else {
                isHoliday = collectionDateSet.contains(current);
            }
            if (!isHoliday) {
                calculatedDays++;
            }
            current = current.plusDays(1);
        }

        if (calculatedDays < 1) {
            throw new IllegalStateException("No hay d\u00edas h\u00e1biles en el rango seleccionado.");
        }

        // Validate no overlapping vacation requests (PENDING or APPROVED)
        Long overlappingCount = VacationRequestEntity.getEntityManager()
                .createQuery(
                    "select count(v) from VacationRequestEntity v where v.userId = ?1 and v.status in ('PENDING', 'APPROVED') and v.startDate <= ?3 and v.endDate >= ?2",
                    Long.class)
                .setParameter(1, userId)
                .setParameter(2, startDate)
                .setParameter(3, endDate)
                .getSingleResult();
        if (overlappingCount > 0) {
            throw new IllegalStateException("Ya tienes una solicitud de vacaciones aprobada o pendiente en el rango seleccionado.");
        }

        int available = bal.getTotalDays() - bal.getUsedDays() - bal.getPendingDays();
        if (calculatedDays > available) {
            throw new IllegalStateException("Saldo insuficiente. Disponible: " + available + ", solicitados: " + calculatedDays);
        }

        VacationRequestEntity entity = new VacationRequestEntity();
        entity.setId(UUID.randomUUID());
        entity.setUserId(userId);
        entity.setProjectId(projectId);
        entity.setStartDate(startDate);
        entity.setEndDate(endDate);
        entity.setBusinessDays(calculatedDays);
        entity.setStatus("PENDING");
        entity.setComment(comment);
        entity.persist();
        entity.flush();

        bal.setPendingDays(bal.getPendingDays() + calculatedDays);
        bal.persist();
        bal.flush();

        LOG.infof("[VACATION] Requested: userId=%s, start=%s, end=%s, days=%d", userId, startDate, endDate, calculatedDays);
        outboxPublisher.publish("VACATION", entity.getId(), "VACATION_REQUESTED",
                "{\"userId\":\"" + userId + "\",\"startDate\":\"" + startDate
                + "\",\"endDate\":\"" + endDate + "\",\"businessDays\":" + calculatedDays + "}");

        return entity;
    }
}
