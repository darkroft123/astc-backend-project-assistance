package com.smms.assistance.infrastructure.controller.graphql.dto;

import lombok.*;
import org.eclipse.microprofile.graphql.Input;
@Input
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JustificationFilterInput {
    private String status;
    private String fromDate;
    private String toDate;
    @Builder.Default
    private int page = 0;
    
    @Builder.Default
    private int size = 20;
}
