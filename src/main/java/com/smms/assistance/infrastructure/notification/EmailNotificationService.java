package com.smms.assistance.infrastructure.notification;

import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@ApplicationScoped
public class EmailNotificationService {

    private static final Logger LOG = Logger.getLogger(EmailNotificationService.class);

    @Inject
    Mailer mailer;

    @ConfigProperty(name = "app.notification.email.enabled", defaultValue = "false")
    boolean enabled;

    public void send(String to, String subject, String body) {
        if (!enabled) {
            LOG.infof("[EMAIL DISABLED] Would send to=%s subject=%s", to, subject);
            return;
        }
        if (to == null || to.isBlank()) {
            LOG.warn("Email not sent: recipient is empty");
            return;
        }
        try {
            mailer.send(Mail.withText(to, subject, body));
            LOG.infof("Email sent to=%s subject=%s", to, subject);
        } catch (Exception e) {
            LOG.errorf(e, "Failed to send email to=%s: %s", to, e.getMessage());
        }
    }

    public void sendJustificationReviewed(String userEmail, String status, String comment) {
        String subject = status.equals("APPROVED")
            ? "[ASTC] Justificación aprobada"
            : status.equals("REJECTED")
            ? "[ASTC] Justificación rechazada"
            : "[ASTC] Correcciones solicitadas";

        String body = "Tu justificación ha sido " +
            (status.equals("APPROVED") ? "aprobada" :
             status.equals("REJECTED") ? "rechazada" :
             "devuelta con observaciones") + ".\n\n";

        if (comment != null && !comment.isBlank()) {
            body += "Comentario del Jefe de Proyecto:\n\"" + comment + "\"\n\n";
        }

        if (status.equals("OBSERVATION")) {
            body += "Por favor corrige y reenvía la justificación.\n\n";
        }

        body += "ASTC - Sistema de Asistencia";
        send(userEmail, subject, body);
    }

    public void sendScheduleAssigned(String userEmail, String projectName, String workStart, String workEnd) {
        send(userEmail,
            "[ASTC] Horario asignado - " + projectName,
            "Se te ha asignado un horario en el proyecto " + projectName + ":\n\n"
            + "Entrada: " + workStart + "\n"
            + "Salida: " + workEnd + "\n\n"
            + "ASTC - Sistema de Asistencia");
    }

    public void sendProjectAssigned(String userEmail, String projectName, String role) {
        send(userEmail,
            "[ASTC] Asignado a proyecto - " + projectName,
            "Has sido asignado al proyecto \"" + projectName + "\" como " + role + ".\n\n"
            + "Ya puedes registrar tu asistencia en este proyecto.\n\n"
            + "ASTC - Sistema de Asistencia");
    }
}
