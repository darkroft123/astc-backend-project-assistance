package com.smms.assistance.infrastructure.controller.graphql.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class CollectionOutput {
    private UUID id;
    private String name;
    private String scope;
    private UUID ownerId;
    private LocalDateTime createdAt;
    private List<CollectionItemOutput> items;
}
