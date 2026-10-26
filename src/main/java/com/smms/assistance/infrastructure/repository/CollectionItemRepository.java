package com.smms.assistance.infrastructure.repository;

import com.smms.assistance.domain.entity.CollectionItemEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class CollectionItemRepository {

    @PersistenceContext
    EntityManager entityManager;

    public List<CollectionItemEntity> findByCollectionId(UUID collectionId) {
        return entityManager.createQuery("select i from CollectionItemEntity i where i.collectionId = ?1 order by i.date asc", CollectionItemEntity.class)
                .setParameter(1, collectionId)
                .getResultList();
    }

    public List<CollectionItemEntity> findByCollectionIds(List<UUID> collectionIds) {
        if (collectionIds == null || collectionIds.isEmpty()) return List.of();
        return entityManager.createQuery("select i from CollectionItemEntity i where i.collectionId in ?1 order by i.date asc", CollectionItemEntity.class)
                .setParameter(1, collectionIds)
                .getResultList();
    }

    public List<CollectionItemEntity> findByProjectId(UUID projectId) {
        return entityManager.createQuery("select i from CollectionItemEntity i join ProjectCollectionEntity pc on i.collectionId = pc.collectionId where pc.projectId = ?1 order by i.date asc", CollectionItemEntity.class)
                .setParameter(1, projectId)
                .getResultList();
    }

    @Transactional
    public void persist(CollectionItemEntity entity) {
        entityManager.persist(entity);
    }

    @Transactional
    public void deleteById(UUID id) {
        entityManager.createQuery("delete from CollectionItemEntity i where i.id = ?1")
                .setParameter(1, id)
                .executeUpdate();
    }
    
    @Transactional
    public void deleteByCollectionId(UUID collectionId) {
        entityManager.createQuery("delete from CollectionItemEntity i where i.collectionId = ?1")
                .setParameter(1, collectionId)
                .executeUpdate();
    }
}
