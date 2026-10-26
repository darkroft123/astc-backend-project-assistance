package com.smms.assistance.application.usecase;

import com.smms.assistance.application.in.ListTeamAbsencesPort;
import com.smms.assistance.domain.entity.AbsenceEntity;
import com.smms.assistance.application.service.TeamMembershipResolver;
import com.smms.assistance.infrastructure.repository.AbsenceRepository;
import com.smms.assistance.infrastructure.security.AuthContext;
import com.smms.assistance.domain.model.Page;

import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class ListTeamAbsencesUseCase implements ListTeamAbsencesPort {

    @Inject
    AuthContext authContext;

    @Inject
    AbsenceRepository absenceRepository;

    @Inject
    TeamMembershipResolver teamMembershipResolver;

    @Override
    public Page<AbsenceEntity> execute(int page, int size, UUID projectId, UUID userId, LocalDate fromDate, LocalDate toDate, String type, Boolean justified) {
        UUID currentPmId = authContext.getUserId();

        List<UUID> userIds = teamMembershipResolver.resolveManagedByPm(userId, currentPmId, false);

        if (userIds.isEmpty()) {
            return Page.<AbsenceEntity>builder()
                    .items(List.of())
                    .page(page)
                    .size(size)
                    .totalItems(0L)
                    .build();
        }

        StringBuilder query = new StringBuilder("userId in :userIds");
        Map<String, Object> params = new HashMap<>();
        params.put("userIds", userIds);

        if (projectId != null) {
            query.append(" and projectId = :projectId");
            params.put("projectId", projectId);
        }
        if (fromDate != null) {
            query.append(" and date >= :from");
            params.put("from", fromDate);
        }
        if (toDate != null) {
            query.append(" and date <= :to");
            params.put("to", toDate);
        }
        if (type != null && !type.isBlank()) {
            query.append(" and type = :type");
            params.put("type", type);
        }
        if (justified != null) {
            query.append(" and justified = :justified");
            params.put("justified", justified);
        }

        io.quarkus.panache.common.Page qPage = io.quarkus.panache.common.Page.of(page, size);
        var panacheQuery = absenceRepository.find(query.toString(), Sort.by("date").descending(), params);

        List<AbsenceEntity> items = panacheQuery.page(qPage).list();
        long total = panacheQuery.count();

        return Page.<AbsenceEntity>builder()
                .items(items)
                .page(page)
                .size(size)
                .totalItems(total)
                .build();
    }
}
