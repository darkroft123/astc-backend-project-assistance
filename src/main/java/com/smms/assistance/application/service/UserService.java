package com.smms.assistance.application.service;

import com.smms.assistance.infrastructure.client.BackofficeClient;
import com.smms.assistance.infrastructure.controller.graphql.dto.UserOutput;
import io.smallrye.graphql.client.typesafe.api.TypesafeGraphQLClientBuilder;
import io.smallrye.graphql.client.typesafe.api.ErrorOr;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.logging.Logger;

import java.util.UUID;
import java.util.List;

@ApplicationScoped
public class UserService {

    private static final Logger LOG = Logger.getLogger(UserService.class);

    @Inject
    JsonWebToken jwt;

    private BackofficeClient getBackofficeClient(String token) {
        var builder = TypesafeGraphQLClientBuilder.newBuilder()
                .configKey("backoffice-service");
        if (token != null && !token.isEmpty()) {
            builder = builder.header("Authorization", "Bearer " + token);
        } else {
            try {
                if (io.quarkus.arc.Arc.container().requestContext().isActive() && jwt != null && jwt.getRawToken() != null) {
                    builder = builder.header("Authorization", "Bearer " + jwt.getRawToken());
                }
            } catch (Exception ignored) {
                // Background thread without active RequestContext
            }
        }
        return builder.build(BackofficeClient.class);
    }

    private BackofficeClient getBackofficeClient() {
        return getBackofficeClient(null);
    }

    public UserOutput fetchUser(UUID userId) {
        return fetchUser(userId, null);
    }

    public UserOutput fetchUser(UUID userId, String token) {
        if (userId == null) return null;
        long start = System.currentTimeMillis();
        LOG.info("📡 [HTTP] Fetching user " + userId + " from backoffice-service");
        try {
            ErrorOr<UserOutput> response = getBackofficeClient(token).getUser(userId.toString());
            long elapsed = System.currentTimeMillis() - start;
            if (response == null || response.hasErrors() || !response.isPresent()) {
                LOG.warn("⚠️ [HTTP] User " + userId + " not found or has errors: " + (response != null ? response.getErrors() : "null"));
                return null;
            }
            LOG.info("✅ [HTTP] User " + userId + " fetched in " + elapsed + "ms");
            return response.get();
        } catch (Exception e) {
            LOG.warn("⚠️ [HTTP] Non-fatal error fetching user " + userId + ": " + e.getMessage());
            return null;
        }
    }

    public List<UserOutput> listUsers() {
        return listUsers(null);
    }

    public List<UserOutput> listUsers(String token) {
        long start = System.currentTimeMillis();
        LOG.info("📡 [HTTP] Fetching ALL users from backoffice-service");
        try {
            ErrorOr<List<UserOutput>> response = getBackofficeClient(token).listUsers();
            if (response == null || response.hasErrors() || !response.isPresent()) {
                LOG.warn("⚠️ [HTTP] GraphQL errors or missing data fetching users: " + (response != null ? response.getErrors() : "null"));
                return List.of();
            }
            long elapsed = System.currentTimeMillis() - start;
            LOG.info("✅ [HTTP] Users list fetched: " + response.get().size() + " users in " + elapsed + "ms");
            return response.get();
        } catch (Exception ex) {
            LOG.warn("⚠️ [HTTP] Non-fatal exception fetching users: " + ex.getMessage());
            return List.of();
        }
    }

    public List<UserOutput> fetchUsersBatch(List<UUID> userIds) {
        return fetchUsersBatch(userIds, null);
    }

    public List<UserOutput> fetchUsersBatch(List<UUID> userIds, String token) {
        if (userIds == null || userIds.isEmpty()) return List.of();
        long start = System.currentTimeMillis();
        try {
            java.util.Set<String> wanted = new java.util.HashSet<>();
            for (UUID id : userIds) wanted.add(id.toString());

            List<UserOutput> results = new java.util.ArrayList<>();
            for (UserOutput u : listUsers(token)) {
                if (u != null && u.id != null && wanted.contains(u.id.toString())) {
                    results.add(u);
                }
            }
            LOG.info("✅ [BATCH] Fetched " + results.size() + "/" + userIds.size() + " users in "
                    + (System.currentTimeMillis() - start) + "ms (single round trip)");
            return results;
        } catch (Exception batchError) {
            LOG.error("❌ [BATCH] Batch fetch failed, falling back to sequential: " + batchError.getMessage());
            List<UserOutput> results = new java.util.ArrayList<>();
            for (UUID id : userIds) {
                try {
                    UserOutput u = fetchUser(id, token);
                    if (u != null && u.id != null) {
                        results.add(u);
                    }
                } catch (Exception e) {
                    LOG.error("❌ [BATCH] Error fetching user " + id + ": " + e.getMessage());
                }
            }
            return results;
        }
    }
}
