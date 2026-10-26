package com.smms.assistance.infrastructure.controller.graphql.dto;

import lombok.*;
import org.eclipse.microprofile.graphql.Type;

import java.util.List;

@Type
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JustificationPageOutput {
    private List<JustificationOutput> items;
    private int page;
    private int size;
    private long total;
}