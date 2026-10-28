package com.smms.assistance.infrastructure.messaging;

import java.time.LocalDateTime;
import java.util.List;

import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.jboss.logging.Logger;

import com.smms.assistance.domain.entity.OutboxEventEntity;
import com.smms.assistance.infrastructure.repository.OutboxRepository;

import com.smms.assistance.infrastructure.client.AuthClient;
import com.smms.assistance.infrastructure.controller.graphql.dto.UserOutput;
import java.util.UUID;

import io.quarkus.runtime.StartupEvent;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class OutboxEventProcessor {

    private static final Logger LOG = Logger.getLogger(OutboxEventProcessor.class);

    @Inject
    OutboxRepository outboxRepository;

    @Inject
    AuthClient authClient;

    @Inject
    com.smms.assistance.application.in.ProjectPort projectPort;

    @Inject
    @Channel("notification-events")
    Emitter<String> notificationEmitter;

    @Inject
    Vertx vertx;

    @ConfigProperty(name = "event.outbox.batch-size", defaultValue = "50")
    int batchSize;

    @ConfigProperty(name = "event.outbox.max-attempts", defaultValue = "10")
    int maxAttempts;

    void init(@Observes StartupEvent ev) {
        vertx.setPeriodic(5000, id -> vertx.executeBlocking(
            promise -> {
                try {
                    processEvents();
                    promise.complete();
                } catch (Exception e) {
                    LOG.errorf(e, "Error processing outbox events");
                    promise.fail(e);
                }
            },
            false
        ));
    }

    @ActivateRequestContext
    void processEvents() {
        List<OutboxEventEntity> events;
        try {
            events = io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().call(() -> 
                outboxRepository.findUnpublished(batchSize)
            );
        } catch (Exception e) {
            LOG.error("Failed to fetch outbox events", e);
            return;
        }
        if (events.isEmpty()) {
            return;
        }

        LOG.infof("📬 [Outbox] Found %d unpublished outbox events to process in project-assistance-service", events.size());

        List<UUID> successfulIds = new java.util.ArrayList<>();

        for (OutboxEventEntity event : events) {
            try {
                io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().run(() -> {
                    LOG.infof("📬 [Outbox] Processing event ID=%s, type=%s, payload=%s",
                            event.getId(), event.getEventType(), event.getPayload());
                    String templateCode = mapEventType(event.getEventType());
                    if (templateCode == null) {
                        LOG.warnf("⚠ [Outbox] Unknown event type '%s' for outbox event %s, marking as processed",
                                event.getEventType(), event.getId());
                        successfulIds.add(event.getId());
                        return;
                    }

                    JsonObject payload = new JsonObject(event.getPayload());
                    String userId = payload.getString("recipientUserId", payload.getString("userId", ""));
                    String toEmail = payload.getString("userEmail", "");
                    String toName = payload.getString("userName", "");

                    if ((toEmail == null || toEmail.isBlank()) && !userId.isBlank()) {
                        try {
                            UserOutput user = authClient.getUser(userId);
                            if (user != null) {
                                toEmail = user.email;
                                toName = user.username;
                                payload.put("userEmail", toEmail);
                                payload.put("userName", toName);
                            }
                        } catch (Exception e) {
                            LOG.warnf("Could not fetch user details from auth-service for userId=%s: %s", userId, e.getMessage());
                        }
                    }

                    if (toEmail == null || toEmail.isBlank()) {
                        toEmail = "dev-notification@astc.local";
                    }

                    // Enriquecer nombre de proyecto
                    String projectIdStr = payload.getString("projectId");
                    if (projectIdStr != null && !projectIdStr.isBlank()) {
                        try {
                            UUID pid = UUID.fromString(projectIdStr);
                            projectPort.findById(pid).ifPresent(p -> {
                                if (p.name != null && !p.name.isBlank()) {
                                    payload.put("PROJECT_NAME", p.name);
                                    payload.put("projectName", p.name);
                                }
                            });
                        } catch (Exception e) {
                            LOG.warnf("Could not resolve project name for projectId=%s", projectIdStr);
                        }
                    }

                    // Enriquecer y traducir roles
                    String rawRole = null;
                    if (payload.containsKey("role")) rawRole = payload.getString("role");
                    else if (payload.containsKey("newRole")) rawRole = payload.getString("newRole");
                    else if (payload.containsKey("memberRole")) rawRole = payload.getString("memberRole");

                    if (rawRole != null && !rawRole.isBlank()) {
                        String transRole = translateRole(rawRole);
                        payload.put("ROLE_NAME", transRole);
                        payload.put("role", transRole);
                        payload.put("roleName", transRole);
                        payload.put("NEW_USER_ROLE", transRole);
                    } else if (templateCode.equals("ASB000005") || templateCode.equals("ASB000007")) {
                        payload.put("ROLE_NAME", "Trabajador");
                    }

                    if (payload.containsKey("previousRole")) {
                        payload.put("previousRole", translateRole(payload.getString("previousRole")));
                    }

                    // Enriquecer y traducir estados / alertas
                    if (payload.containsKey("status") && payload.getString("status") != null) {
                        payload.put("STATUS", translateStatus(payload.getString("status")));
                    } else if (!payload.containsKey("STATUS") || payload.getString("STATUS") == null || payload.getString("STATUS").isBlank()) {
                        payload.put("STATUS", "Pendiente");
                    }
                    if (payload.containsKey("type") && payload.getString("type") != null) {
                        payload.put("ALERT_TYPE", translateStatus(payload.getString("type")));
                    } else if (!payload.containsKey("ALERT_TYPE") || payload.getString("ALERT_TYPE") == null || payload.getString("ALERT_TYPE").isBlank()) {
                        payload.put("ALERT_TYPE", "Alerta general");
                    }

                    JsonObject msg = new JsonObject();
                    msg.put("notificationCode", templateCode);
                    msg.put("type", "EMAIL");
                    msg.put("userId", userId);
                    msg.put("toEmail", toEmail);
                    msg.put("toName", toName);
                    msg.put("variables", payload);

                    LOG.infof("📬 [Outbox] Dispatching payload to RabbitMQ channel 'notification-events': %s", msg.encode());
                    notificationEmitter.send(msg.encode());
                    LOG.infof("✅ [Outbox] Successfully published outbox event %s (type=%s) as %s to=%s",
                            event.getId(), event.getEventType(), templateCode, toEmail);

                    successfulIds.add(event.getId());
                });
            } catch (Exception e) {
                LOG.errorf(e, "❌ [Outbox] Failed to process outbox event %s (type=%s)",
                        event.getId(), event.getEventType());
                try {
                    io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().run(() -> markFailedAttempt(event));
                } catch (Exception ex) {
                    LOG.errorf(ex, "❌ [Outbox] Failed to record failed attempt for event %s", event.getId());
                }
            }
        }

        if (!successfulIds.isEmpty()) {
            try {
                io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().run(() -> {
                    outboxRepository.getEntityManager().createNativeQuery(
                            "UPDATE outbox_events SET published = true, published_at = now(), status = 'PUBLISHED', last_error = null WHERE id = ANY(:ids)")
                            .setParameter("ids", successfulIds.toArray(new UUID[0]))
                            .executeUpdate();
                });
                LOG.infof("✅ [Outbox] Bulk updated %d events as PUBLISHED", successfulIds.size());
            } catch (Exception e) {
                LOG.error("Failed to perform bulk update for published outbox events, executing fallback", e);
                for (UUID id : successfulIds) {
                    try {
                        io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().run(() -> {
                            outboxRepository.getEntityManager().createNativeQuery(
                                    "UPDATE outbox_events SET published = true, published_at = now(), status = 'PUBLISHED', last_error = null WHERE id = :id")
                                    .setParameter("id", id)
                                    .executeUpdate();
                        });
                    } catch (Exception ex) {
                        LOG.errorf(ex, "Failed fallback update for outbox event %s", id);
                    }
                }
            }
        }
    }

    private void markPublished(OutboxEventEntity event) {
        outboxRepository.findByIdOptional(event.getId()).ifPresent(managed -> {
            managed.setPublished(true);
            managed.setPublishedAt(LocalDateTime.now());
            managed.setLastError(null);
            managed.setStatus("PUBLISHED");
        });
    }

    private void markFailedAttempt(OutboxEventEntity event) {
        outboxRepository.findByIdOptional(event.getId()).ifPresent(managed -> {
            int attempts = managed.getAttempts() == null ? 0 : managed.getAttempts();
            managed.setAttempts(attempts + 1);
            if (attempts + 1 >= maxAttempts) {
                LOG.errorf("💀 [Outbox] Event %s exceeded max attempts (%d), marking as FAILED",
                        event.getId(), maxAttempts);
                managed.setPublished(true);
                managed.setPublishedAt(LocalDateTime.now());
                managed.setStatus("FAILED");
                managed.setLastError("Exceeded max attempts");
            } else {
                managed.setStatus("PENDING");
                managed.setLastError("Processing error, retry pending");
            }
        });
    }

    private String mapEventType(String eventType) {
        switch (eventType) {
            case "JUSTIFICATION_APPROVED":
                return "ASP000001";
            case "JUSTIFICATION_REJECTED":
                return "ASP000002";
            case "JUSTIFICATION_OBSERVED":
                return "ASP000003";
            case "SCHEDULE_ASSIGNED":
                return "ASP000004";
            case "SCHEDULE_MODIFIED":
                return "ASP000005";
            case "SCHEDULE_DELETED":
                return "ASP000006";
            case "SCHEDULE_DEFAULT_CHANGED":
                return "ASP000007";
            case "ALERT_CREATED":
                return "ASP000017";
            case "ALERT_APPROVED":
                return "ASP000022";
            case "ALERT_CANCELLED":
                return "ASP000023";
            case "PROJECT_CREATED":
                return "ASB000011";
            case "PROJECT_UPDATED":
                return "ASB000012";
            case "PROJECT_DELETED":
                return "ASB000013";
            case "PROJECT_MEMBER_ADDED":
                return "ASB000005";
            case "PROJECT_MEMBER_REMOVED":
                return "ASB000006";
            case "PROJECT_MEMBER_ROLE_CHANGED":
                return "ASB000007";
            case "VACATION_REQUESTED":
                return "ASP000024";
            case "VACATION_APPROVED":
                return "ASP000025";
            case "VACATION_REJECTED":
                return "ASP000026";
            case "VACATION_CANCELLED":
                return "ASP000027";
            default:
                return null;
        }
    }

    private static String translateRole(String role) {
        if (role == null || role.isBlank()) return "Trabajador";
        switch (role.trim().toUpperCase()) {
            case "SUPER_ADMIN":
            case "ADMIN":
            case "ADMINISTRATOR":
                return "Admin";
            case "PROJECT_MANAGER":
            case "PROJECT MANAGER":
            case "MANAGER":
            case "LEAD":
                return "Jefe de Proyecto";
            case "TEAM_MEMBER":
            case "TEAM MEMBER":
            case "DEVELOPER":
            case "MEMBER":
            case "USER":
            default:
                return "Trabajador";
        }
    }

    private static String translateStatus(String val) {
        if (val == null || val.isBlank()) return val;
        switch (val.trim().toUpperCase()) {
            case "NO_PHOTO": return "Sin fotografía";
            case "LOCATION_MISMATCH": return "Ubicación fuera de rango";
            case "FACE_MISMATCH": return "Rostro no coincide";
            case "UNKNOWN_DEVICE": return "Dispositivo no reconocido";
            case "PENDING": return "Pendiente";
            case "APPROVED": return "Aprobada";
            case "REJECTED": return "Rechazada";
            case "CANCELLED": return "Cancelada";
            case "OBSERVED": return "Observada";
            case "OBSERVATION": return "Observada";
            case "REQUESTED": return "Solicitadas";
            default: return val;
        }
    }
}
