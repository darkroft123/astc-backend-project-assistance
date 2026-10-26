package com.smms.assistance.domain.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "collections")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CollectionEntity extends PanacheEntityBase {

    @Id
    private UUID id;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "scope", nullable = false, length = 20)
    private String scope; // GLOBAL, PROJECT

    @Column(name = "owner_id")
    private UUID ownerId; // null if GLOBAL, projectId if PROJECT

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
