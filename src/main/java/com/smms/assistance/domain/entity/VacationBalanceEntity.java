package com.smms.assistance.domain.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "vacation_balance")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VacationBalanceEntity extends PanacheEntityBase {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "total_days", nullable = false)
    private Integer totalDays;

    @Column(name = "used_days", nullable = false)
    private Integer usedDays;

    @Column(name = "pending_days", nullable = false)
    private Integer pendingDays;

    @Column(name = "year", nullable = false)
    private Integer year;

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
