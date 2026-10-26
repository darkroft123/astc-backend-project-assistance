// domain/entity/Notification.java
package com.smms.assistance.domain.entity;
import lombok.*;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class Notification extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "user_id")
    public UUID userId;

    public String type;
    public String severity;
    public String title;

    @Column(columnDefinition = "TEXT")
    public String message;

    @Column(name = "reference_type")
    public String referenceType;

    @Column(name = "reference_id")
    public UUID referenceId;

    @Column(name = "created_at")
    public LocalDateTime createdAt;

    public Notification() {}

    // Constructor útil
    public Notification(UUID id, UUID userId, String type, String severity, String title,
                        String message, String referenceType, UUID referenceId, LocalDateTime createdAt) {
        this.id = id;
        this.userId = userId;
        this.type = type;
        this.severity = severity;
        this.title = title;
        this.message = message;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.createdAt = createdAt;
    }
}