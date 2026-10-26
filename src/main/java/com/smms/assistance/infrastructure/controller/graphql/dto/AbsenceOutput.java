package com.smms.assistance.infrastructure.controller.graphql.dto;

import lombok.*;
import org.eclipse.microprofile.graphql.Type;

import java.util.UUID;

@Type
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AbsenceOutput {

    private UUID id;
    private UUID userId;
    private UUID projectId;
    private String date;
    private String type;
    private Boolean justified;
}
