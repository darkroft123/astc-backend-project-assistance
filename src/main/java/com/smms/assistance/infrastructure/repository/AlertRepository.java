package com.smms.assistance.infrastructure.repository;

import com.smms.assistance.domain.entity.AlertEntity;
import com.smms.assistance.domain.model.Page;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class AlertRepository implements PanacheRepositoryBase<AlertEntity, UUID> {

    /**
     * Lista alertas con paginación y total en una sola query nativa parametrizada. Usa created_at::date (no lesiona el
     * índice en Postgres) y COUNT(*) OVER() para evitar el patrón list+count (2 queries -> 1).
     */
    public Page<AlertEntity> findPageByCriteria(
            String status,
            String type,
            LocalDate from,
            LocalDate to,
            int page,
            int size,
            UUID scopedToUserId) {

        StringBuilder sql = new StringBuilder(
                "SELECT a.id, a.user_id, a.project_id, a.type, a.status, a.detail, a.latitude, a.longitude, " +
                "a.created_at, a.updated_at, COUNT(*) OVER() AS total " +
                "FROM alerts a WHERE 1 = 1");
        Map<String, Object> params = new HashMap<>();

        if (scopedToUserId != null) {
            List<UUID> projectIds = getEntityManager()
                    .createNativeQuery("SELECT DISTINCT project_id FROM project_members WHERE user_id = :uid", UUID.class)
                    .setParameter("uid", scopedToUserId)
                    .getResultList();
            if (projectIds.isEmpty()) {
                return Page.<AlertEntity>builder()
                        .items(List.of())
                        .page(page)
                        .size(size)
                        .totalItems(0)
                        .build();
            }
            sql.append(" AND a.project_id IN (:projectIds)");
            params.put("projectIds", projectIds);
        }

        if (status != null && !status.isBlank()) {
            sql.append(" AND a.status = :status");
            params.put("status", status);
        }
        if (type != null && !type.isBlank()) {
            sql.append(" AND a.type = :type");
            params.put("type", type);
        }
        if (from != null) {
            sql.append(" AND a.created_at::date >= :from");
            params.put("from", from);
        }
        if (to != null) {
            sql.append(" AND a.created_at::date <= :to");
            params.put("to", to);
        }
        sql.append(" ORDER BY a.created_at DESC LIMIT :limit OFFSET :offset");

        var query = getEntityManager().createNativeQuery(sql.toString());
        params.forEach(query::setParameter);
        query.setParameter("limit", size);
        query.setParameter("offset", (long) Math.max(0, page - 1) * size);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        long total = rows.isEmpty() ? 0L : ((Number) rows.get(0)[10]).longValue();

        List<AlertEntity> items = rows.stream().map(AlertRepository::toEntity).toList();

        return Page.<AlertEntity>builder()
                .items(items)
                .page(page)
                .size(size)
                .totalItems(total)
                .build();
    }

    private static AlertEntity toEntity(Object[] r) {
        AlertEntity e = new AlertEntity();
        e.setId(r[0] instanceof UUID u ? u : UUID.fromString(r[0].toString()));
        e.setUserId(r[1] instanceof UUID u ? u : UUID.fromString(r[1].toString()));
        e.setProjectId(r[2] == null ? null : (r[2] instanceof UUID u ? u : UUID.fromString(r[2].toString())));
        e.setType((String) r[3]);
        e.setStatus((String) r[4]);
        e.setDetail(r[5] != null ? r[5].toString() : null);
        e.setLatitude(r[6] == null ? null : ((Number) r[6]).doubleValue());
        e.setLongitude(r[7] == null ? null : ((Number) r[7]).doubleValue());
        e.setCreatedAt(r[8] == null ? null : LocalDateTime.parse(r[8].toString().replace(' ', 'T')));
        e.setUpdatedAt(r[9] == null ? null : LocalDateTime.parse(r[9].toString().replace(' ', 'T')));
        return e;
    }

    public List<AlertEntity> findByCriteria(
            String status,
            String type,
            LocalDate from,
            LocalDate to,
            int page,
            int size) {

        StringBuilder query = new StringBuilder("1 = 1");
        Map<String, Object> params = new HashMap<>();

        if (status != null && !status.isBlank()) {
            query.append(" and status = :status");
            params.put("status", status);
        }
        if (type != null && !type.isBlank()) {
            query.append(" and type = :type");
            params.put("type", type);
        }
        if (from != null) {
            // Assuming we check 'createdAt' date part
            query.append(" and DATE(createdAt) >= :from");
            params.put("from", from);
        }
        if (to != null) {
            query.append(" and DATE(createdAt) <= :to");
            params.put("to", to);
        }

        return find(query.toString(), Sort.by("createdAt").descending(), params)
                .page(io.quarkus.panache.common.Page.of(page, size))
                .list();
    }

    public long countByCriteria(
            String status,
            String type,
            LocalDate from,
            LocalDate to) {

        StringBuilder query = new StringBuilder("1 = 1");
        Map<String, Object> params = new HashMap<>();

        if (status != null && !status.isBlank()) {
            query.append(" and status = :status");
            params.put("status", status);
        }
        if (type != null && !type.isBlank()) {
            query.append(" and type = :type");
            params.put("type", type);
        }
        if (from != null) {
            query.append(" and DATE(createdAt) >= :from");
            params.put("from", from);
        }
        if (to != null) {
            query.append(" and DATE(createdAt) <= :to");
            params.put("to", to);
        }

        return count(query.toString(), params);
    }
}
