package com.smms.assistance.infrastructure.repository;

import com.smms.assistance.domain.entity.CollectionEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class CollectionRepository {

    @PersistenceContext
    EntityManager entityManager;

    public List<CollectionEntity> findGlobalCollections() {
        return entityManager.createQuery("select c from CollectionEntity c where c.scope = 'GLOBAL' order by c.createdAt desc", CollectionEntity.class)
                .getResultList();
    }

    public List<CollectionEntity> findProjectCollections(UUID projectId) {
        return entityManager.createQuery("select c from CollectionEntity c where c.scope = 'PROJECT' and c.ownerId = ?1 order by c.createdAt desc", CollectionEntity.class)
                .setParameter(1, projectId)
                .getResultList();
    }

    public List<CollectionEntity> findLinkedCollectionsForProject(UUID projectId) {
        return entityManager.createQuery("select c from CollectionEntity c join ProjectCollectionEntity pc on c.id = pc.collectionId where pc.projectId = ?1 order by c.createdAt desc", CollectionEntity.class)
                .setParameter(1, projectId)
                .getResultList();
    }

    /**
     * Devuelve las colecciones de un proyecto (propias + vinculadas) en una sola query nativa parametrizada (UNION),
     * evitando las 2 queries casi idénticas previas (findProjectCollections + findLinkedCollectionsForProject).
     */
    public List<CollectionEntity> findCollectionsForProjectUnified(UUID projectId) {
        return entityManager.createNativeQuery(
                "SELECT c.* FROM collections c " +
                "WHERE c.scope = 'PROJECT' AND c.owner_id = :projectId " +
                "UNION " +
                "SELECT c.* FROM collections c " +
                "JOIN project_collections pc ON pc.collection_id = c.id " +
                "WHERE pc.project_id = :projectId " +
                "ORDER BY created_at DESC", CollectionEntity.class)
                .setParameter("projectId", projectId)
                .getResultList();
    }

    @Transactional
    public void persist(CollectionEntity entity) {
        entityManager.persist(entity);
    }

    @Transactional
    public void deleteById(UUID id) {
        entityManager.createQuery("delete from CollectionEntity c where c.id = ?1")
                .setParameter(1, id)
                .executeUpdate();
    }
}
