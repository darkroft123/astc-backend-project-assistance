package com.smms.assistance.domain.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "holidays")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HolidayEntity extends PanacheEntityBase {

    @Id
    private UUID id;

    @Column(name = "type", nullable = false, length = 20)
    private String type; // GLOBAL, PROJECT, USER_INCLUDE, USER_EXCLUDE

    @Column(name = "target_id")
    private UUID targetId; // null si es GLOBAL, projectId si es PROJECT, userId si es USER_*

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
