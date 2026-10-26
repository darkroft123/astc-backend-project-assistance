package com.smms.assistance.domain.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import lombok.*;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name="projects")
public class Project extends PanacheEntityBase {

    @Id
    public UUID id;

    public String name;

    @Column(columnDefinition="TEXT")
    public String description;

    public String status;

    @Column(name="start_date")
    public LocalDate startDate;

    @Column(name="end_date")
    public LocalDate endDate;

    public BigDecimal budget;

    public String currency;

    @Column(name="created_at")
    public LocalDateTime createdAt;

    @Column(name="updated_at")
    public LocalDateTime updatedAt;

    @Column(name="deleted_at")
    public LocalDateTime deletedAt;

    @Column(name = "work_start_time")
    public LocalTime workStartTime;

    @Column(name = "work_end_time")
    public LocalTime workEndTime;

    @Column(name = "grace_minutes")
    public Integer graceMinutes;

    @Column(name = "timezone", length = 50)
    public String timezone;

    @Column(name = "responsible_id")
    public UUID responsibleId;

    public Double latitude;
    public Double longitude;
    public Double radius;

    @Column(name = "absence_cutoff_time")
    public LocalTime absenceCutoffTime;

    @Column(name = "vacation_eligibility_days")
    public Integer vacationEligibilityDays;
}