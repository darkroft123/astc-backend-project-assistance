package com.smms.assistance.infrastructure.repository;

import com.smms.assistance.domain.entity.ProjectCollectionEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

import java.util.UUID;

@ApplicationScoped
public class ProjectCollectionRepository {

    @PersistenceContext
    EntityManager entityManager;

    @Transactional
    public void persist(ProjectCollectionEntity entity) {
        entityManager.persist(entity);
    }

    @Transactional
    public void unlinkProjectFromCollection(UUID projectId, UUID collectionId) {
        entityManager.createQuery("delete from ProjectCollectionEntity pc where pc.projectId = ?1 and pc.collectionId = ?2")
                .setParameter(1, projectId)
                .setParameter(2, collectionId)
                .executeUpdate();
    }

    @Transactional
    public void unlinkAllFromProject(UUID projectId) {
        entityManager.createQuery("delete from ProjectCollectionEntity pc where pc.projectId = ?1")
                .setParameter(1, projectId)
                .executeUpdate();
    }

    public UUID findActiveCollectionIdForProject(UUID projectId) {
        try {
            return (UUID) entityManager.createQuery("select pc.collectionId from ProjectCollectionEntity pc where pc.projectId = ?1")
                    .setParameter(1, projectId)
                    .setMaxResults(1)
                    .getSingleResult();
        } catch (Exception e) {
            return null;
        }
    }
}
