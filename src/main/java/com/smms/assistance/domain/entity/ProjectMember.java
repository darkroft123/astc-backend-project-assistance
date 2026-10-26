package com.smms.assistance.domain.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import lombok.*;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name="project_members")
public class ProjectMember
        extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name="project_id")
    public UUID projectId;

    @Column(name="user_id")
    public UUID userId;

    public String role;

    @Column(name="created_at")
    public LocalDateTime createdAt;

    @Column(name="updated_at")
    public LocalDateTime updatedAt;
}