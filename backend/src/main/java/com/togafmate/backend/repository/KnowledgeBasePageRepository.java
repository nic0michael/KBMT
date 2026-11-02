package com.togafmate.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.togafmate.backend.entity.KnowledgeBasePage;

/** Provides persistence operations for Markdown pages. */
public interface KnowledgeBasePageRepository extends JpaRepository<KnowledgeBasePage, UUID> {
    /** Opens an ordered cursor over legacy Markdown pages. */
    @Query("select page from KnowledgeBasePage page where page.knowledgeBase.id = :knowledgeBaseId order by page.title")
    Stream<KnowledgeBasePage> streamAllByKnowledgeBaseId(@Param("knowledgeBaseId") UUID knowledgeBaseId);
    /** Lists pages in a Knowledge Base in title order. */
    List<KnowledgeBasePage> findAllByKnowledgeBaseIdOrderByTitleAsc(UUID knowledgeBaseId);
    /** Checks whether a title is already used in a Knowledge Base. */
    boolean existsByKnowledgeBaseIdAndTitleIgnoreCase(UUID knowledgeBaseId, String title);
    /** Removes legacy Markdown pages belonging to a Knowledge Base. */
    void deleteAllByKnowledgeBaseId(UUID knowledgeBaseId);
    /** Finds a page belonging to the specified Knowledge Base. */
    Optional<KnowledgeBasePage> findByIdAndKnowledgeBaseId(UUID id, UUID knowledgeBaseId);
}
