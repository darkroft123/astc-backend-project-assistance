package com.smms.assistance.infrastructure.security;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.UUID;

@RequestScoped
public class AuthContext {

    @Inject
    JsonWebToken jwt;

    private static final UUID DEV_USER =
        UUID.fromString("11111111-1111-1111-1111-111111111111");

    public UUID getUserId() {
        if (jwt == null || jwt.getSubject() == null) {
            // 🔥 MODO DEV (sin auth)
            return DEV_USER;
        }
        return UUID.fromString(jwt.getSubject());
    }

    public String getEmail() {
        return jwt != null ? jwt.getClaim("email") : null;
    }

    public String getRole() {
        if (jwt == null || jwt.getClaim("role") == null) {
            return "TEAM_MEMBER"; // modo dev
        }
        return jwt.getClaim("role");
    }

    public void assertTeamMember() {
        // 🔥 Permitir en modo dev
        if (jwt == null || jwt.getSubject() == null) {
            return;
        }

        if (!"TEAM_MEMBER".equals(getRole())) {
            throw new SecurityException("Access denied: requires TEAM_MEMBER role");
        }
    }

    public void assertProjectManager() {
        // 🔥 Permitir en modo dev
        if (jwt == null || jwt.getSubject() == null) {
            return;
        }

        if (!"PROJECT_MANAGER".equals(getRole())) {
            throw new SecurityException("Access denied: requires PROJECT_MANAGER role");
        }
    }

    public void assertAdmin() {
        // 🔥 Permitir en modo dev
        if (jwt == null || jwt.getSubject() == null) {
            return;
        }

        if (!"ADMIN".equals(getRole())) {
            throw new SecurityException("Access denied: requires ADMIN role");
        }
    }

    public void assertAdminOrProjectManager() {
        // 🔥 Permitir en modo dev
        if (jwt == null || jwt.getSubject() == null) {
            return;
        }

        String role = getRole();
        if (!"ADMIN".equals(role) && !"PROJECT_MANAGER".equals(role)) {
            throw new SecurityException("Access denied: requires ADMIN or PROJECT_MANAGER role");
        }
    }

    public boolean isAdmin() {
        return "ADMIN".equals(getRole());
    }
}