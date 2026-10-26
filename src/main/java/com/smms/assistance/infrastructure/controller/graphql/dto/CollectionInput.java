package com.smms.assistance.infrastructure.controller.graphql.dto;

import lombok.Data;
import org.eclipse.microprofile.graphql.Input;

import java.util.UUID;

@Data
@Input("CollectionInput")
public class CollectionInput {
    public String name;
    public String scope; // GLOBAL, PROJECT
    public UUID ownerId; // null or projectId
}
