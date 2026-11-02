package com.togafmate.backend.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** Stores the content of a Markdown page uploaded to a Knowledge Base. */
@Entity
@Table(name = "knowledge_base_pages", uniqueConstraints = @UniqueConstraint(
        name = "uk_kb_page_title", columnNames = { "knowledge_base_id", "title" }))
public class KnowledgeBasePage {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "knowledge_base_id", nullable = false)
    private KnowledgeBase knowledgeBase;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected KnowledgeBasePage() {
    }

    /** Creates a page with its Knowledge Base, title, and Markdown source. */
    public KnowledgeBasePage(KnowledgeBase knowledgeBase, String title, String content) {
        this.knowledgeBase = knowledgeBase;
        this.title = title;
        this.content = content;
        this.createdAt = Instant.now();
    }

    /** Returns the page identifier. */
    public UUID getId() { return id; }
    /** Returns the page title derived from the uploaded filename. */
    public String getTitle() { return title; }
    /** Returns the stored Markdown source. */
    public String getContent() { return content; }
    /** Returns the page creation time. */
    public Instant getCreatedAt() { return createdAt; }
}
