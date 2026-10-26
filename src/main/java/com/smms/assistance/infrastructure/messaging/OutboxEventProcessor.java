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
}
