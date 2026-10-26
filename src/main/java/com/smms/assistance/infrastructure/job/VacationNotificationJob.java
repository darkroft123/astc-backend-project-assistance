package com.smms.assistance.infrastructure.job;

import com.smms.assistance.domain.entity.Notification;
import com.smms.assistance.domain.entity.VacationRequestEntity;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.jboss.logging.Logger;

@ApplicationScoped
public class VacationNotificationJob {

    private static final Logger LOG = Logger.getLogger(VacationNotificationJob.class);

    @Scheduled(cron = "0 0 8 * * ?")
    @Transactional
    void checkEndingVacations() {
        LOG.info("[VacationNotificationJob] Checking ending vacations...");
        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);
        LocalDate yesterday = today.minusDays(1);
        int sent = 0;

        // 1 single query for yesterday, today, and tomorrow
        List<VacationRequestEntity> vacations = VacationRequestEntity
                .find("status = 'APPROVED' and endDate in (?1, ?2, ?3)", yesterday, today, tomorrow)
                .list();

        for (VacationRequestEntity v : vacations) {
            LocalDate endDate = v.getEndDate();
            String type;
            String title;
            String message;

            if (endDate.equals(today)) {
                type = "VACATION_ENDS_TODAY";
                title = "Tus vacaciones terminan hoy";
                message = "Tus vacaciones aprobadas finalizan hoy. Mañana debes reincorporarte a tus labores.";
            } else if (endDate.equals(tomorrow)) {
                type = "VACATION_ENDS_TOMORROW";
                title = "Tus vacaciones terminan mañana";
                message = "Tus vacaciones aprobadas finalizan mañana. Recuerda planificar tu regreso.";
            } else if (endDate.equals(yesterday)) {
                type = "VACATION_ENDED";
                title = "Tus vacaciones han terminado";
                message = "Tus vacaciones aprobadas finalizaron ayer. Si no te has reincorporado, regulariza tu asistencia.";
            } else {
                continue;
            }

            // Deduplication: check if notification already exists for this user+type+date
            long existing = Notification.count(
                    "userId = ?1 AND type = ?2 AND createdAt >= ?3 AND createdAt < ?4",
                    v.getUserId(), type, today.atStartOfDay(), today.plusDays(1).atStartOfDay());
            if (existing > 0) {
                LOG.infof("[VacationNotificationJob] Notification already exists for user %s type %s, skipping",
                        v.getUserId(), type);
                continue;
            }

            Notification n = new Notification(
                    UUID.randomUUID(),
                    v.getUserId(),
                    type,
                    "INFO",
                    title,
                    message,
                    "VACATION",
                    v.getId(),
                    LocalDateTime.now()
            );
            n.persist();
            sent++;
            LOG.infof("[VacationNotificationJob] Notification sent to user %s for vacation %s", v.getUserId(), v.getId());
        }

        LOG.infof("[VacationNotificationJob] Finished. Notifications sent: %d", sent);
    }
}
