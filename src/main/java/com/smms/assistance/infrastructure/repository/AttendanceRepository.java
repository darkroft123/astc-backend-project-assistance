package com.smms.assistance.infrastructure.repository;

import com.smms.assistance.domain.entity.AttendanceEntity;
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
public class AttendanceRepository implements PanacheRepositoryBase<AttendanceEntity, UUID> {

    public List<AttendanceEntity> findByUser(
            UUID userId,
            UUID projectId,
            LocalDate from,
            LocalDate to,
            String status,
            int page,
            int size) {

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
        if (status != null && !status.isBlank()) {
            query.append(" and status = :status");
            params.put("status", status);
        }

        return find(query.toString(), Sort.by("date").descending(), params)
                .page(Page.of(page, size))
                .list();
    }

    public long countByUser(
            UUID userId,
            UUID projectId,
            LocalDate from,
            LocalDate to,
            String status) {

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
        if (status != null && !status.isBlank()) {
            query.append(" and status = :status");
            params.put("status", status);
        }

        return count(query.toString(), params);
    }

    public long countByUserId(UUID userId) {
        return count("userId", userId);
    }

    public List<AttendanceEntity> findRecentByUser(UUID userId, int limit) {
        return find("userId = :userId", Sort.by("date").descending(),
                Map.of("userId", userId))
                .page(Page.of(0, limit))
                .list();
    }

    public List<AttendanceEntity> findByCriteriaPM(
            UUID projectId,
            UUID userId,
            LocalDate from,
            LocalDate to,
            String status,
            int page,
            int size) {

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
        if (status != null && !status.isBlank()) {
            query.append(" and status = :status");
            params.put("status", status);
        }

        return find(query.toString(), Sort.by("date").descending(), params)
                .page(Page.of(page, size))
                .list();
    }

    public long countByCriteriaPM(
            UUID projectId,
            UUID userId,
            LocalDate from,
            LocalDate to,
            String status) {

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
        if (status != null && !status.isBlank()) {
            query.append(" and status = :status");
            params.put("status", status);
        }

        return count(query.toString(), params);
    }
}