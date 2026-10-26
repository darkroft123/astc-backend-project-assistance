package com.smms.assistance.infrastructure.controller.graphql.dto;

import io.smallrye.graphql.api.federation.FieldSet;
import io.smallrye.graphql.api.federation.Key;
import lombok.*;
import org.eclipse.microprofile.graphql.Type;

import java.util.UUID;

@Type
@Key(fields = @FieldSet("id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceOutput {

    private UUID id;
    private UUID userId;
    private UUID projectId;
    private String date;
    private String checkIn;
    private String checkOut;
    private String status;
    private Double latitude;
    private Double longitude;
    private String photoUrl;
}