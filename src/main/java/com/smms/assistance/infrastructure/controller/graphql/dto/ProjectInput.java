package com.smms.assistance.infrastructure.controller.graphql.dto;

import org.eclipse.microprofile.graphql.Input;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Input("ProjectInput")
public class ProjectInput {

    public String name;

    public String description;

    public String status;

    public LocalDate startDate;

    public LocalDate endDate;

    public BigDecimal budget;

    public String currency;

    // 🕒 HORARIO DEL PROYECTO
    public LocalTime workStartTime;

    public LocalTime workEndTime;

    public Integer graceMinutes;

    // 🌍 ZONA HORARIA DEL PROYECTO (IANA timezone, e.g. "America/Lima")
    public String timezone;

    // 👤 RESPONSABLE (PROJECT MANAGER)
    public UUID responsibleId;

    // ⏰ HORA DE CORTE PARA FALTAS AUTOMÁTICAS
    public LocalTime absenceCutoffTime;

    // 👥 MIEMBROS DEL PROYECTO (TEAM MEMBERS)
    public List<ProjectMemberInput> members;

    // 🏖️ DÍAS REQUERIDOS PARA VACACIONES (DEFAULT 90 = 3 MESES)
    public Integer vacationEligibilityDays;

    // 🔴 DÍAS FESTIVOS
    public List<String> holidays;
}