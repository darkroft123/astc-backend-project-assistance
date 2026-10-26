package com.smms.assistance.infrastructure.controller.graphql.dto;

import lombok.Data;
import org.eclipse.microprofile.graphql.Input;
import java.time.LocalDate;

@Data
@Input("CollectionItemInput")
public class CollectionItemInput {
    private LocalDate date;
    private String name;
}
