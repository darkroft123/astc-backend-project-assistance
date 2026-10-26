package com.smms.assistance.infrastructure.controller.graphql.dto;

import com.smms.assistance.domain.entity.*;
import com.smms.assistance.domain.model.*;
import com.smms.assistance.shared.util.TimezoneService;

import java.util.Map;
import java.util.stream.Collectors;

public final class DtoMapper {

    private DtoMapper() {}

    private static final Map<String, String> STATUS_TRANSLATIONS = Map.ofEntries(
        Map.entry("ON_TIME", "ASISTENCIA"),
        Map.entry("ON_TIME_CHECKED_OUT", "ASISTENCIA"),
        Map.entry("LATE", "TARDE"),
        Map.entry("LATE_CHECKED_OUT", "TARDE"),
        Map.entry("EARLY", "ASISTENCIA"),
        Map.entry("OUTSIDE_SCHEDULE", "ASISTENCIA"),
        Map.entry("EARLY_DEPARTURE", "ASISTENCIA"),
        Map.entry("CHECKED_IN", "ASISTENCIA"),
        Map.entry("PRESENTE", "ASISTENCIA"),
        Map.entry("FALTA", "FALTA"),
        Map.entry("FALTA_AUTOMATICA", "FALTA"),
        Map.entry("UNJUSTIFIED", "FALTA")
    );

    public static String translateStatus(String raw) {
        if (raw == null) return "";
        String translated = STATUS_TRANSLATIONS.get(raw.toUpperCase());
        return translated != null ? translated : raw;
    }

    public static String utcToZone(java.time.LocalDateTime utc, String targetZoneId) {
        if (utc == null || targetZoneId == null) return null;
        return TimezoneService.convertUtcToZone(utc, targetZoneId);
    }

    // =========================
    // ATTENDANCE
    // =========================
    public static AttendanceOutput toAttendanceOutput(AttendanceEntity e) {
        return AttendanceOutput.builder()
                .id(e.getId())
                .userId(e.getUserId())
                .projectId(e.getProjectId())
                .date(e.getDate() != null ? e.getDate().toString() : null)
                .checkIn(e.getCheckIn() != null ? e.getCheckIn().toString() : null)
                .checkOut(e.getCheckOut() != null ? e.getCheckOut().toString() : null)
                .status(translateStatus(e.getStatus()))
                .latitude(e.getLatitude())
                .longitude(e.getLongitude())
                .photoUrl(e.getPhotoUrl())
                .build();
    }

    // =========================
    // JUSTIFICATION
    // =========================
    public static JustificationOutput toJustificationOutput(JustificationEntity e) {
        return JustificationOutput.builder()
                .id(e.getId())
                .absenceId(e.getAbsenceId())
                .userId(e.getUserId())
                .description(e.getDescription())
                .documentUrl(e.getDocumentUrl())
                .status(translateStatus(e.getStatus()))
                .comment(e.getComment())
                .submittedAt(e.getSubmittedAt() != null ? utcToZone(e.getSubmittedAt(), TimezoneService.defaultTimezone()) : null)
                .reviewedAt(e.getReviewedAt() != null ? utcToZone(e.getReviewedAt(), TimezoneService.defaultTimezone()) : null)
                .absenceDate(e.getAbsenceDate())
                .absenceType(e.getAbsenceType())
                .history(e.getHistory())
                .build();
    }

    // =========================
    // ALERT
    public static AlertOutput toAlertOutput(AlertEntity e) {
        return AlertOutput.builder()
                .id(e.getId())
                .type(e.getType())
                .status(translateStatus(e.getStatus()))
                .detail(e.getDetail())
                .userId(e.getUserId())
                .projectId(e.getProjectId())
                .createdAt(e.getCreatedAt())
                .latitude(e.getLatitude())
                .longitude(e.getLongitude())
                .build();
    }

    public static AlertDetailOutput toAlertDetailOutput(AlertEntity e) {
        String severity = "MEDIUM";
        if ("FACE_FRAUD".equals(e.getType()) || "FACE_MISMATCH".equals(e.getType())) {
            severity = "CRITICAL";
        } else if ("OUT_OF_AREA".equals(e.getType())) {
            severity = "HIGH";
        }
        return AlertDetailOutput.builder()
                .id(e.getId())
                .type(e.getType())
                .status(translateStatus(e.getStatus()))
                .detail(e.getDetail())
                .latitude(e.getLatitude())
                .longitude(e.getLongitude())
                .createdAt(e.getCreatedAt())
                .userId(e.getUserId())
                .projectId(e.getProjectId())
                .severity(severity)
                .build();
    }

    // =========================
    // ABSENCE
    // =========================
    public static AbsenceOutput toAbsenceOutput(AbsenceEntity e) {
        return AbsenceOutput.builder()
                .id(e.getId())
                .userId(e.getUserId())
                .projectId(e.getProjectId())
                .date(e.getDate() != null ? e.getDate().toString() : null)
                .type(e.getType())
                .justified(e.getJustified())
                .build();
    }

    public static AbsencePageOutput toAbsencePage(Page<AbsenceEntity> p) {
        return AbsencePageOutput.builder()
                .items(p.getItems().stream()
                        .map(DtoMapper::toAbsenceOutput)
                        .collect(Collectors.toList()))
                .page(p.getPage())
                .size(p.getSize())
                .total(p.getTotalItems())
                .build();
    }

    // =========================
    // DASHBOARD
    // =========================
    public static DashboardOutput toDashboardOutput(DashboardSummary s) {
        return DashboardOutput.builder()
                .totalAttendances(s.getTotalAttendances())
                .totalAbsences(s.getTotalAbsences())
                .pendingJustifications(s.getPendingJustifications())
                .build();
    }

    // =========================
    // PAGINATION
    // =========================
    public static AttendancePageOutput toAttendancePage(Page<AttendanceEntity> p) {
        return AttendancePageOutput.builder()
                .items(p.getItems().stream()
                        .map(DtoMapper::toAttendanceOutput)
                        .collect(Collectors.toList()))
                .page(p.getPage())
                .size(p.getSize())
                .total(p.getTotalItems())
                .build();
    }

    public static JustificationPageOutput toJustificationPage(Page<JustificationEntity> p) {
        return JustificationPageOutput.builder()
                .items(p.getItems().stream()
                        .map(DtoMapper::toJustificationOutput)
                        .collect(Collectors.toList()))
                .page(p.getPage())
                .size(p.getSize())
                .total(p.getTotalItems())
                .build();
    }

    public static AlertPageOutput toAlertPage(Page<AlertEntity> p) {
        return AlertPageOutput.builder()
                .items(p.getItems().stream()
                        .map(DtoMapper::toAlertOutput)
                        .collect(Collectors.toList()))
                .pageInfo(PageInfoOutput.builder()
                        .page(p.getPage())
                        .size(p.getSize())
                        .totalItems(p.getTotalItems())
                        .totalPages(p.getSize() == 0 ? 0 :
                                (int) Math.ceil((double) p.getTotalItems() / p.getSize()))
                        .build())
                .total(p.getTotalItems())
                .page(p.getPage())
                .size(p.getSize())
                .build();
    }

    // =========================
    // 🔥 PROJECT (NUEVO Y CLAVE)
    // =========================
    public static ProjectOutput toProjectOutput(Project p) {

        ProjectOutput o = new ProjectOutput();

        o.id = p.id;
        o.name = p.name;
        o.description = p.description;
        o.status = p.status;

        o.startDate = p.startDate;
        o.endDate = p.endDate;

        o.budget = p.budget;
        o.currency = p.currency;

        o.workStartTime =
        p.workStartTime != null ? p.workStartTime.toString() : null;

            o.workEndTime =
                    p.workEndTime != null ? p.workEndTime.toString() : null;
        o.graceMinutes = p.graceMinutes;
        o.timezone = p.timezone;
        o.absenceCutoffTime = p.absenceCutoffTime != null ? p.absenceCutoffTime.toString() : null;

        o.createdAt = p.createdAt;

        return o;
    }
}