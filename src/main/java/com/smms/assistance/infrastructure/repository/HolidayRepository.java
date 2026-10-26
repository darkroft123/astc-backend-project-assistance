package com.smms.assistance.infrastructure.repository;

import com.smms.assistance.domain.entity.HolidayEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class HolidayRepository {

    @PersistenceContext
    EntityManager entityManager;

    public List<HolidayEntity> findByTargetIdAndDateRange(UUID targetId, LocalDate fromDate, LocalDate toDate) {
        return entityManager
                .createQuery("select h from HolidayEntity h where h.targetId = ?1 and h.date >= ?2 and h.date <= ?3 order by h.date", HolidayEntity.class)
                .setParameter(1, targetId)
                .setParameter(2, fromDate)
                .setParameter(3, toDate)
                .getResultList();
    }

    /**
     * Determina si una fecha es feriado para un usuario dentro de un proyecto, en una sola query nativa parametrizada.
     * Precedencia (igual que la lógica original): USER_EXCLUDE < USER_INCLUDE < colección de feriados del proyecto.
     */
    public boolean isHolidayForUser(UUID projectId, UUID userId, LocalDate date) {
        Object result = entityManager.createNativeQuery(
                "SELECT CASE " +
                "  WHEN EXISTS (SELECT 1 FROM holidays h WHERE h.date = :date AND h.type = 'USER_EXCLUDE' AND h.target_id = :userId) THEN false " +
                "  WHEN EXISTS (SELECT 1 FROM holidays h WHERE h.date = :date AND h.type = 'USER_INCLUDE' AND h.target_id = :userId) THEN true " +
                "  WHEN EXISTS (SELECT 1 FROM holidays h WHERE h.date = :date AND h.type = 'PROJECT' AND h.target_id = :projectId) THEN true " +
                "  WHEN EXISTS (SELECT 1 FROM holidays h WHERE h.date = :date AND h.type = 'GLOBAL') THEN true " +
                "  WHEN EXISTS (SELECT 1 FROM collection_items ci JOIN project_collections pc ON pc.collection_id = ci.collection_id " +
                "                WHERE ci.date = :date AND pc.project_id = :projectId) THEN true " +
                "  ELSE false END")
                .setParameter("date", date)
                .setParameter("userId", userId)
                .setParameter("projectId", projectId)
                .getSingleResult();
        if (result instanceof Boolean) {
            return (Boolean) result;
        } else if (result instanceof Number) {
            return ((Number) result).intValue() != 0;
        }
        return false;
    }

    /**
     * Carga en una sola query nativa parametrizada todos los feriados relevantes para un usuario/proyecto en un rango
     * (GLOBAL, PROJECT del proyecto, y USER_INCLUDE/USER_EXCLUDE del usuario). Evita el N+1 por día de requestVacation.
     */
    public List<HolidayEntity> findRelevantInRange(UUID userId, UUID projectId, LocalDate fromDate, LocalDate toDate) {
        return entityManager.createNativeQuery(
                "SELECT h.* FROM holidays h WHERE h.date BETWEEN :from AND :to AND (" +
                "  (h.type IN ('USER_INCLUDE','USER_EXCLUDE') AND h.target_id = :userId) " +
                "  OR (h.type = 'PROJECT' AND h.target_id = :projectId) " +
                "  OR h.type = 'GLOBAL')", HolidayEntity.class)
                .setParameter("from", fromDate)
                .setParameter("to", toDate)
                .setParameter("userId", userId)
                .setParameter("projectId", projectId)
                .getResultList();
    }

    /**
     * Devuelve en una sola query nativa parametrizada las fechas de feriado (items de colecciones) vinculadas a un
     * proyecto dentro de un rango.
     */
    public List<LocalDate> findCollectionHolidayDatesInRange(UUID projectId, LocalDate fromDate, LocalDate toDate) {
        List<?> rawResults = entityManager.createNativeQuery(
                "SELECT DISTINCT ci.date FROM collection_items ci " +
                "JOIN project_collections pc ON pc.collection_id = ci.collection_id " +
                "WHERE pc.project_id = :projectId AND ci.date BETWEEN :from AND :to")
                .setParameter("projectId", projectId)
                .setParameter("from", fromDate)
                .setParameter("to", toDate)
                .getResultList();
        return rawResults.stream()
                .map(row -> {
                    if (row instanceof LocalDate ld) return ld;
                    if (row instanceof java.sql.Date sd) return sd.toLocalDate();
                    if (row instanceof java.time.LocalDateTime ldt) return ldt.toLocalDate();
                    if (row instanceof java.util.Date ud) return new java.sql.Date(ud.getTime()).toLocalDate();
                    return LocalDate.parse(row.toString());
                })
                .toList();
    }

    @Transactional
    public void persist(HolidayEntity entity) {
        entityManager.persist(entity);
    }

    @Transactional
    public void deleteById(UUID id) {
        entityManager.createQuery("delete from HolidayEntity h where h.id = ?1")
                .setParameter(1, id)
                .executeUpdate();
    }

    @Transactional
    public void deleteByTargetId(UUID targetId) {
        entityManager.createQuery("delete from HolidayEntity h where h.targetId = ?1")
                .setParameter(1, targetId)
                .executeUpdate();
    }

    @Transactional
    public void deleteAllHolidays(UUID targetId, String type) {
        String holidayType = type != null ? type : (targetId == null ? "GLOBAL" : "PROJECT");
        if (targetId == null) {
            entityManager.createQuery("delete from HolidayEntity h where h.type = ?1 and h.targetId is null")
                    .setParameter(1, holidayType)
                    .executeUpdate();
        } else {
            entityManager.createQuery("delete from HolidayEntity h where h.type = ?1 and h.targetId = ?2")
                    .setParameter(1, holidayType)
                    .setParameter(2, targetId)
                    .executeUpdate();
        }
    }

    public List<HolidayEntity> findByTargetId(UUID targetId) {
        return entityManager.createQuery("select h from HolidayEntity h where h.targetId = ?1 order by h.date", HolidayEntity.class)
                .setParameter(1, targetId)
                .getResultList();
    }

    public List<HolidayEntity> findGlobalHolidays() {
        return entityManager.createQuery("select h from HolidayEntity h where h.type = 'GLOBAL' order by h.date", HolidayEntity.class)
                .getResultList();
    }

    public List<HolidayEntity> findByTypeAndTargetId(String type, UUID targetId) {
        if (targetId == null) {
            return entityManager.createQuery("select h from HolidayEntity h where h.type = ?1 and h.targetId is null", HolidayEntity.class)
                    .setParameter(1, type)
                    .getResultList();
        } else {
            return entityManager.createQuery("select h from HolidayEntity h where h.type = ?1 and h.targetId = ?2", HolidayEntity.class)
                    .setParameter(1, type)
                    .setParameter(2, targetId)
                    .getResultList();
        }
    }
}
