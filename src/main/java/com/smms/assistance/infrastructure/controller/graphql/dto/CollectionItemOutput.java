package com.smms.assistance.infrastructure.controller.graphql.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class CollectionItemOutput {
    private UUID id;
    private UUID collectionId;
    private LocalDate date;
    private String name;
}
