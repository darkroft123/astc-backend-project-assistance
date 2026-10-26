// infrastructure/controller/graphql/dto/NotificationInput.java
package com.smms.assistance.infrastructure.controller.graphql.dto;

import org.eclipse.microprofile.graphql.Input;
import java.util.UUID;

@Input("NotificationInput")
public class NotificationInput {

    public UUID userId;
    public String type;
    public String title;
    public String message;

    // Campos recomendados (opcionales)
    public String severity;        // INFO, WARNING, URGENT, CRITICAL
    public String referenceType;   // PROJECT, TASK, etc.
    public UUID referenceId;

    // Constructor vacío (importante para GraphQL)
    public NotificationInput() {}

    // Constructor útil para pruebas
    public NotificationInput(UUID userId, String type, String title, String message) {
        this.userId = userId;
        this.type = type;
        this.title = title;
        this.message = message;
    }
}