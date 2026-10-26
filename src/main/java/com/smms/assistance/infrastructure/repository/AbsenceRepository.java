package com.smms.assistance.infrastructure.repository;

import com.smms.assistance.domain.entity.AbsenceEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class AbsenceRepository implements PanacheRepositoryBase<AbsenceEntity, UUID> {

    public List<AbsenceEntity> findByUser(UUID userId, UUID projectId, LocalDate from, LocalDate to,
                                          String type, Boolean justified,
                                          int page, int size) {
        StringBuilder query = new StringBuilder("userId = :userId");
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);

        if (projectId != null) {
            query.append(" and projectId = :projectId");
            params.put("projectId", projectId);
        }
        if (from != null) {
            query.append(" and date >= :from");
            params.put("from", from);
        }
        if (to != null) {
            query.append(" and date <= :to");
            params.put("to", to);
        }
        if (type != null && !type.isBlank()) {
            query.append(" and type = :type");
            params.put("type", type);
        }
        if (justified != null) {
            query.append(" and justified = :justified");
            params.put("justified", justified);
        }

        return find(query.toString(), Sort.by("date").descending(), params)
                .page(Page.of(page, size))
                .list();
    }

    public long countByUser(UUID userId, UUID projectId, LocalDate from, LocalDate to,
                            String type, Boolean justified) {
        StringBuilder query = new StringBuilder("userId = :userId");
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);

        if (projectId != null) {
            query.append(" and projectId = :projectId");
            params.put("projectId", projectId);
        }
        if (from != null) {
            query.append(" and date >= :from");
            params.put("from", from);
        }
        if (to != null) {
            query.append(" and date <= :to");
            params.put("to", to);
        }
        if (type != null && !type.isBlank()) {
            query.append(" and type = :type");
            params.put("type", type);
        }
        if (justified != null) {
            query.append(" and justified = :justified");
            params.put("justified", justified);
        }

        return count(query.toString(), params);
    }

    public List<AbsenceEntity> findByCriteriaPM(UUID projectId, UUID userId, LocalDate from, LocalDate to,
                                          String type, Boolean justified,
                                          int page, int size) {
        StringBuilder query = new StringBuilder("1 = 1");
        Map<String, Object> params = new HashMap<>();

        if (projectId != null) {
            query.append(" and projectId = :projectId");
            params.put("projectId", projectId);
        }
        if (userId != null) {
            query.append(" and userId = :userId");
            params.put("userId", userId);
        }
        if (from != null) {
            query.append(" and date >= :from");
            params.put("from", from);
        }
        if (to != null) {
            query.append(" and date <= :to");
            params.put("to", to);
        }
        if (type != null && !type.isBlank()) {
            query.append(" and type = :type");
            params.put("type", type);
        }
        if (justified != null) {
            query.append(" and justified = :justified");
            params.put("justified", justified);
        }

        return find(query.toString(), Sort.by("date").descending(), params)
                .page(Page.of(page, size))
                .list();
    }

    public long countByCriteriaPM(UUID projectId, UUID userId, LocalDate from, LocalDate to,
                            String type, Boolean justified) {
        StringBuilder query = new StringBuilder("1 = 1");
        Map<String, Object> params = new HashMap<>();

        if (projectId != null) {
            query.append(" and projectId = :projectId");
            params.put("projectId", projectId);
        }
        if (userId != null) {
            query.append(" and userId = :userId");
            params.put("userId", userId);
        }
        if (from != null) {
            query.append(" and date >= :from");
            params.put("from", from);
        }
        if (to != null) {
            query.append(" and date <= :to");
            params.put("to", to);
        }
        if (type != null && !type.isBlank()) {
            query.append(" and type = :type");
            params.put("type", type);
        }
        if (justified != null) {
            query.append(" and justified = :justified");
            params.put("justified", justified);
        }

        return count(query.toString(), params);
    }
}
