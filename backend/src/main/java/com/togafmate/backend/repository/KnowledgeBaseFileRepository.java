package com.togafmate.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.togafmate.backend.entity.KnowledgeBaseFile;

/** Provides persistence operations for Knowledge Base resources. */
public interface KnowledgeBaseFileRepository extends JpaRepository<KnowledgeBaseFile, UUID> {
    /** Opens an ordered cursor over a Knowledge Base's resources. */
    @Query("select file from KnowledgeBaseFile file where file.knowledgeBase.id = :knowledgeBaseId order by file.path")
    Stream<KnowledgeBaseFile> streamAllByKnowledgeBaseId(@Param("knowledgeBaseId") UUID knowledgeBaseId);
    /** Lists resources in logical path order. */
    List<KnowledgeBaseFile> findAllByKnowledgeBaseIdOrderByPathAsc(UUID knowledgeBaseId);
    /** Checks whether a logical path is already present. */
    boolean existsByKnowledgeBaseIdAndPath(UUID knowledgeBaseId, String path);
    /** Finds a resource belonging to a Knowledge Base. */
    Optional<KnowledgeBaseFile> findByKnowledgeBaseIdAndPath(UUID knowledgeBaseId, String path);
    /** Finds a resource by ID within the specified Knowledge Base. */
    Optional<KnowledgeBaseFile> findByIdAndKnowledgeBaseId(UUID id, UUID knowledgeBaseId);
    /** Removes all resources belonging to a Knowledge Base. */
    void deleteAllByKnowledgeBaseId(UUID knowledgeBaseId);
}
