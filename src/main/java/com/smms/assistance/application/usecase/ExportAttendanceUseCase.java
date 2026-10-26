package com.smms.assistance.application.usecase;

import com.smms.assistance.application.in.ExportAttendancePort;
import com.smms.assistance.application.service.TeamMembershipResolver;
import com.smms.assistance.application.service.UserService;
import com.smms.assistance.domain.entity.Project;
import com.smms.assistance.infrastructure.client.AssistanceClientWrapper;
import com.smms.assistance.infrastructure.controller.graphql.dto.ExportResultOutput;
import com.smms.assistance.infrastructure.controller.graphql.dto.UserOutput;
import com.smms.assistance.infrastructure.security.AuthContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class ExportAttendanceUseCase implements ExportAttendancePort {

    @Inject
    AssistanceClientWrapper clientWrapper;

    @Inject
    AuthContext authContext;

    @Inject
    TeamMembershipResolver teamMembershipResolver;

    @Inject
    UserService userService;

    @Override
    public ExportResultOutput execute(UUID projectId, UUID userId, LocalDate fromDate, LocalDate toDate, String status) {
        UUID currentPmId = authContext.getUserId();

        // 1. Resolve project members
        List<UUID> userIds = teamMembershipResolver.resolve(projectId, userId, currentPmId);

        System.out.println("[PM Attendance] Exporting attendance for PM: " + currentPmId);

        if (userIds.isEmpty()) {
            return ExportResultOutput.builder()
                    .fileName("asistencias_" + LocalDate.now() + ".csv")
                    .content("\uFEFFID;Colaborador;Email;Proyecto;Fecha;Hora Entrada;Hora Salida;Estado;Ubicación\n")
                    .build();
        }

        // 2. Preload project map
        Map<UUID, String> projectMap = new HashMap<>();
        try {
            List<Project> projects = Project.listAll();
            for (Project p : projects) {
                if (p != null && p.id != null) {
                    projectMap.put(p.id, p.name != null ? p.name : "Sin nombre");
                }
            }
        } catch (Exception e) {
            System.err.println("[Export CSV] Warning loading projects: " + e.getMessage());
        }

        // 3. Preload user map
        Map<UUID, UserOutput> userMap = new HashMap<>();
        try {
            List<UserOutput> users = userService.fetchUsersBatch(userIds);
            for (UserOutput u : users) {
                if (u != null && u.id != null) {
                    userMap.put(u.id, u);
                }
            }
        } catch (Exception e) {
            System.err.println("[Export CSV] Warning loading users: " + e.getMessage());
        }

        String fromStr = fromDate != null ? fromDate.toString() : null;
        String toStr = toDate != null ? toDate.toString() : null;

        // Fetch up to 1000000 records for export
        var clientResponse = clientWrapper.getClient().listTeamAttendancePM(
                userIds, projectId, fromStr, toStr, status, 0, 1000000
        );

        StringBuilder csv = new StringBuilder();
        csv.append("\uFEFF"); // UTF-8 BOM for Excel to detect encoding and cells
        csv.append("ID;Colaborador;Email;Proyecto;Fecha;Hora Entrada;Hora Salida;Estado;Ubicación\n");

        if (clientResponse != null && clientResponse.getItems() != null) {
            for (var a : clientResponse.getItems()) {
                UUID recordUserId = a.getUserId();
                UserOutput userObj = recordUserId != null ? userMap.get(recordUserId) : null;

                String colabName = "Usuario Desconocido";
                String email = "";

                if (userObj != null) {
                    String fn = userObj.firstName != null ? userObj.firstName.trim() : "";
                    String ln = userObj.lastName != null ? userObj.lastName.trim() : "";
                    String fullName = (fn + " " + ln).trim();
                    if (!fullName.isEmpty()) {
                        colabName = fullName;
                    } else if (userObj.username != null && !userObj.username.isBlank()) {
                        colabName = userObj.username;
                    }
                    email = userObj.email != null ? userObj.email : "";
                }

                UUID recordProjId = a.getProjectId();
                String projectName = recordProjId != null ? projectMap.getOrDefault(recordProjId, "Sin proyecto") : "Sin proyecto";

                String checkInFormatted = formatTime(a.getCheckIn());
                String checkOutFormatted = formatTime(a.getCheckOut());
                String statusFormatted = formatStatus(a.getStatus());

                String location = resolveDistrict(a.getLatitude(), a.getLongitude());

                csv.append(String.format("%s;%s;%s;%s;%s;%s;%s;%s;%s\n",
                        escapeCsv(a.getId() != null ? a.getId().toString() : ""),
                        escapeCsv(colabName),
                        escapeCsv(email),
                        escapeCsv(projectName),
                        escapeCsv(a.getDate() != null ? a.getDate().toString() : ""),
                        escapeCsv(checkInFormatted),
                        escapeCsv(checkOutFormatted),
                        escapeCsv(statusFormatted),
                        escapeCsv(location)));
            }
        }

        return ExportResultOutput.builder()
                .fileName("asistencias_" + LocalDate.now() + ".csv")
                .content(csv.toString())
                .build();
    }

    private final Map<String, String> districtCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
    private final java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(3))
            .build();

    private String resolveDistrict(Double lat, Double lng) {
        if (lat == null || lng == null) {
            return "Sin ubicación";
        }

        String key = String.format(java.util.Locale.US, "%.4f,%.4f", lat, lng);
        if (districtCache.containsKey(key)) {
            return districtCache.get(key);
        }

        try {
            String url = String.format(java.util.Locale.US,
                    "https://nominatim.openstreetmap.org/reverse?format=json&lat=%.6f&lon=%.6f&zoom=14&addressdetails=1",
                    lat, lng);

            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(url))
                    .header("User-Agent", "ASTC-Assistance-App/1.0 (contact@astc.com)")
                    .header("Accept-Language", "es-PE,es;q=0.9")
                    .timeout(java.time.Duration.ofSeconds(3))
                    .GET()
                    .build();

            java.net.http.HttpResponse<String> response = httpClient.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200 && response.body() != null && !response.body().isBlank()) {
                com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(response.body());
                com.fasterxml.jackson.databind.JsonNode addr = root.path("address");

                if (!addr.isMissingNode()) {
                    String district = getFirstText(addr, "city_district", "suburb", "neighbourhood", "quarter", "town", "village", "city", "county", "state");
                    if (district != null && !district.isBlank()) {
                        districtCache.put(key, district.trim());
                        return district.trim();
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[Export CSV] Warning resolving district: " + e.getMessage());
        }

        String fallback = String.format(java.util.Locale.US, "%.4f, %.4f", lat, lng);
        districtCache.put(key, fallback);
        return fallback;
    }

    private String getFirstText(com.fasterxml.jackson.databind.JsonNode node, String... fields) {
        for (String field : fields) {
            com.fasterxml.jackson.databind.JsonNode val = node.path(field);
            if (!val.isMissingNode() && !val.asText().isBlank()) {
                return val.asText();
            }
        }
        return null;
    }

    private String formatTime(String timeStr) {
        if (timeStr == null || timeStr.isBlank() || "null".equalsIgnoreCase(timeStr)) {
            return "--:--";
        }
        String formatted = timeStr.trim();
        if (formatted.contains("T")) {
            formatted = formatted.substring(formatted.indexOf("T") + 1);
        }
        if (formatted.contains(".")) {
            formatted = formatted.substring(0, formatted.indexOf("."));
        }
        return formatted;
    }

    private String formatStatus(String status) {
        if (status == null || status.isBlank()) return "Desconocido";
        switch (status.toUpperCase()) {
            case "ON_TIME":
            case "PRESENT":
            case "PRESENTE":
                return "Puntual";
            case "LATE":
            case "TARDE":
                return "Tardanza";
            case "LATE_CHECKED_OUT":
                return "Tardanza (Con Salida)";
            case "EARLY_DEPARTURE":
                return "Salida Temprana";
            case "OUTSIDE_SCHEDULE":
                return "Fuera de Horario";
            case "OUTSIDE_SCHEDULE_CHECKED_OUT":
                return "Fuera de Horario (Con Salida)";
            case "FALTA":
            case "ABSENCE":
                return "Falta";
            default:
                return status;
        }
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(";") || value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}

