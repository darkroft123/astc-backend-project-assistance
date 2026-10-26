package com.smms.assistance.application.service;

import com.smms.assistance.infrastructure.controller.graphql.dto.UserOutput;
import jakarta.enterprise.context.RequestScoped;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Caché request-scoped de usuarios resueltos en lote (getMyProjects/getAllProjects/getProjectById). Evita que el
 * resolver GraphQL user(@Source) dispare N llamadas HTTP seriales por miembro. Como es @RequestScoped no se comparte
 * entre peticiones concurrentes.
 */
@RequestScoped
public class UserBatchCache {

    private final Map<UUID, UserOutput> cache = new HashMap<>();

    public UserOutput get(UUID userId) {
        return cache.get(userId);
    }

    public void put(UUID userId, UserOutput user) {
        cache.put(userId, user);
    }
}