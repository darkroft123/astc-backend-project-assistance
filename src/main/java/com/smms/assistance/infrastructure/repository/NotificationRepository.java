package com.smms.assistance.infrastructure.repository;

import com.smms.assistance.application.in.NotificationPort;
import com.smms.assistance.domain.entity.Notification;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class NotificationRepository implements PanacheRepository<Notification>, NotificationPort {

    // ==================== CREATE ====================
    @Override
    @Transactional
    public Notification create(Notification notification) {

        persist(notification);
        return notification;
    }

    // ==================== QUERIES ====================
    @Override
    public List<Notification> findAlertsByUserId(UUID userId) {

        return find(
                "userId = ?1 and type = 'ALERT'",
                Sort.by("createdAt", Sort.Direction.Descending),
                userId
        ).page(0, 1000).list();
    }

    @Override
    public List<Notification> findAllAlerts() {
        return find(
                "type = 'ALERT'",
                Sort.by("createdAt", Sort.Direction.Descending)
        ).page(0, 1000).list();
    }

    // ==================== DELETE ====================
    @Override
    @Transactional
    public boolean delete(UUID id) {
        return delete("id = ?1", id) > 0;
    }

    @Override
public List<Notification> findByUserIdAndType(
        UUID userId,
        String type
) {

    return find(
            "userId = ?1 and type = ?2",
            Sort.by("createdAt", Sort.Direction.Descending),
            userId,
            type
    ).page(0, 1000).list();
}

}