package com.smms.assistance.domain.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalTime;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "schedule_overrides")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleEntity extends PanacheEntityBase {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "work_start_time")
    private LocalTime workStartTime;

    @Column(name = "work_end_time")
    private LocalTime workEndTime;

    @Column(name = "grace_minutes")
    private Integer graceMinutes;

    @Column(name = "shift_type")
    private String shiftType;

    @Column(name = "monday_start")
    private LocalTime mondayStart;

    @Column(name = "monday_end")
    private LocalTime mondayEnd;

    @Column(name = "tuesday_start")
    private LocalTime tuesdayStart;

    @Column(name = "tuesday_end")
    private LocalTime tuesdayEnd;

    @Column(name = "wednesday_start")
    private LocalTime wednesdayStart;

    @Column(name = "wednesday_end")
    private LocalTime wednesdayEnd;

    @Column(name = "thursday_start")
    private LocalTime thursdayStart;

    @Column(name = "thursday_end")
    private LocalTime thursdayEnd;

    @Column(name = "friday_start")
    private LocalTime fridayStart;

    @Column(name = "friday_end")
    private LocalTime fridayEnd;

    @Column(name = "saturday_start")
    private LocalTime saturdayStart;

    @Column(name = "saturday_end")
    private LocalTime saturdayEnd;

    @Column(name = "sunday_start")
    private LocalTime sundayStart;

    @Column(name = "sunday_end")
    private LocalTime sundayEnd;

    @Column(name = "rotation_work_days")
    private Integer rotationWorkDays;

    @Column(name = "rotation_rest_days")
    private Integer rotationRestDays;

    @Column(name = "rotation_shift_start_time")
    private LocalTime rotationShiftStartTime;

    @Column(name = "rotation_shift_end_time")
    private LocalTime rotationShiftEndTime;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Column(name = "absence_cutoff_time")
    private LocalTime absenceCutoffTime;

    @Column(name = "timezone")
    private String timezone;

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
