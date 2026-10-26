package com.smms.assistance.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "absences")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AbsenceEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false, length = 30)
    private String type;

    @Column(nullable = false)
    private Boolean justified = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime utcNow = Instant.now().atZone(ZoneOffset.UTC).toLocalDateTime();
        this.createdAt = utcNow;
        this.updatedAt = utcNow;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now().atZone(ZoneOffset.UTC).toLocalDateTime();
    }
}
