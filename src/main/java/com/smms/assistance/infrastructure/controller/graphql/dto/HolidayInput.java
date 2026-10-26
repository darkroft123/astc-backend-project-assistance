package com.smms.assistance.infrastructure.controller.graphql.dto;

import org.eclipse.microprofile.graphql.Input;

import java.time.LocalDate;
import java.util.UUID;

@Input("HolidayInput")
public class HolidayInput {
    public String type; // GLOBAL, PROJECT, USER_INCLUDE, USER_EXCLUDE
    public UUID targetId;
    public LocalDate date;
    public String name;
}
