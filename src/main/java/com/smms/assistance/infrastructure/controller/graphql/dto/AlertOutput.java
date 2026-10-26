package com.smms.assistance.infrastructure.controller.graphql.dto;

import io.smallrye.graphql.api.federation.FieldSet;
import io.smallrye.graphql.api.federation.Key;
import lombok.*;
import org.eclipse.microprofile.graphql.Type;

import java.util.UUID;

@Type
@Key(fields = @FieldSet("id"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertOutput {
    private UUID id;
    private String type;
    private String status;
    private String detail;
    private UUID userId;
    private UUID projectId;
    private java.time.LocalDateTime createdAt;
    private Double latitude;
    private Double longitude;

    public String getMessage() {
        return detail;
    }
}