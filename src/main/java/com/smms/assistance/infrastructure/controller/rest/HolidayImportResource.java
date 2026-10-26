package com.smms.assistance.infrastructure.controller.rest;

import com.smms.assistance.domain.entity.HolidayEntity;
import com.smms.assistance.infrastructure.controller.graphql.dto.HolidayOutput;
import com.smms.assistance.infrastructure.controller.rest.dto.CsvUploadForm;
import com.smms.assistance.infrastructure.repository.HolidayRepository;
import com.smms.assistance.infrastructure.security.AuthContext;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.MultipartForm;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Path("/rest/holidays")
public class HolidayImportResource {

    @Inject
    HolidayRepository holidayRepository;

    @Inject
    AuthContext authContext;

    @POST
    @Path("/import")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response importCsv(
            @MultipartForm CsvUploadForm form,
            @QueryParam("targetId") UUID targetId,
            @QueryParam("type") String type,
            @QueryParam("defaultName") String defaultName
    ) {
        try {
            authContext.assertAdminOrProjectManager();

            if (form == null || form.file == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("{\"error\":\"Archivo CSV obligatorio\"}")
                        .build();
            }

            String holidayType = type != null && !type.isEmpty() ? type : (targetId == null ? "GLOBAL" : "PROJECT");
            String nameDefault = defaultName != null && !defaultName.isEmpty() ? defaultName : "Feriado";

            String csvContent = new String(form.file.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);

            List<HolidayEntity> existing = holidayRepository.findByTypeAndTargetId(holidayType, targetId);
            Set<LocalDate> existingDates = existing.stream()
                    .map(HolidayEntity::getDate)
                    .collect(Collectors.toSet());

            List<HolidayOutput> created = new ArrayList<>();
            int skipped = 0;
            int errors = 0;

            String[] lines = csvContent.split("\\n");
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.isEmpty()) continue;

                if (i == 0) {
                    String lower = line.toLowerCase();
                    if (lower.startsWith("date") || lower.startsWith("fecha") || lower.startsWith("feriado")) {
                        continue;
                    }
                }

                try {
                    String dateStr;
                    String holidayName = nameDefault;

                    if (line.contains(",")) {
                        String[] parts = line.split(",", 2);
                        dateStr = parts[0].trim();
                        if (parts.length > 1 && !parts[1].trim().isEmpty()) {
                            holidayName = parts[1].trim();
                        }
                    } else {
                        dateStr = line;
                    }

                    if (dateStr.isEmpty()) continue;

                    LocalDate date = LocalDate.parse(dateStr);

                    if (existingDates.contains(date)) {
                        skipped++;
                        continue;
                    }
                    existingDates.add(date);

                    HolidayEntity entity = HolidayEntity.builder()
                            .id(UUID.randomUUID())
                            .type(holidayType)
                            .targetId(targetId)
                            .date(date)
                            .name(holidayName)
                            .createdAt(LocalDateTime.now())
                            .build();
                    holidayRepository.persist(entity);
                    created.add(HolidayOutput.builder()
                            .id(entity.getId())
                            .type(entity.getType())
                            .targetId(entity.getTargetId())
                            .date(entity.getDate())
                            .name(entity.getName())
                            .build());
                } catch (Exception e) {
                    errors++;
                }
            }

            String json = String.format(
                    "{\"total\":%d,\"created\":%d,\"skipped\":%d,\"errors\":%d,\"holidays\":%s}",
                    created.size() + skipped + errors,
                    created.size(),
                    skipped,
                    errors,
                    toJsonArray(created)
            );

            return Response.ok(json).build();

        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\":\"" + e.getMessage() + "\"}")
                    .build();
        }
    }

    private String toJsonArray(List<HolidayOutput> holidays) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < holidays.size(); i++) {
            if (i > 0) sb.append(",");
            HolidayOutput h = holidays.get(i);
            sb.append(String.format(
                    "{\"id\":\"%s\",\"type\":\"%s\",\"targetId\":%s,\"date\":\"%s\",\"name\":\"%s\"}",
                    h.getId(),
                    h.getType() != null ? h.getType() : "",
                    h.getTargetId() != null ? "\"" + h.getTargetId() + "\"" : "null",
                    h.getDate(),
                    h.getName()
            ));
        }
        sb.append("]");
        return sb.toString();
    }
}
