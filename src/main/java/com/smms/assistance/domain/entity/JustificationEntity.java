package com.smms.assistance.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "justifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JustificationEntity {

    @Id
    private UUID id;

    @Column(name = "absence_id", nullable = false)
    private UUID absenceId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    private String description;

    @Column(name = "document_url", length = 500)
    private String documentUrl;

    @Column(nullable = false, length = 30)
    private String status;

    private String comment;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Transient
    private String absenceDate;

    @Transient
    private String absenceType;

    @Transient
    private List<com.smms.assistance.infrastructure.controller.graphql.dto.JustificationHistoryOutput> history;

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
