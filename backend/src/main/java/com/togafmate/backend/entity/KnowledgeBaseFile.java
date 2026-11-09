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

/** Stores a Knowledge Base resource and its logical path in PostgreSQL. */
@Entity
@Table(name = "knowledge_base_files", uniqueConstraints = @UniqueConstraint(
        name = "uk_kb_file_path", columnNames = { "knowledge_base_id", "logical_path" }))
public class KnowledgeBaseFile {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "knowledge_base_id", nullable = false)
    private KnowledgeBase knowledgeBase;

    @Column(name = "logical_path", nullable = false, length = 1024)
    private String path;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "file_content", nullable = false, columnDefinition = "bytea")
    private byte[] content;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected KnowledgeBaseFile() { }

    /** Creates a resource row. */
    public KnowledgeBaseFile(KnowledgeBase knowledgeBase, String path, String contentType, byte[] content) {
        this.knowledgeBase = knowledgeBase;
        this.path = path;
        this.contentType = contentType;
        this.content = content.clone();
        this.createdAt = Instant.now();
    }

    /** Returns the row identifier. */
    public UUID getId() { return id; }
    /** Returns the owning Knowledge Base. */
    public KnowledgeBase getKnowledgeBase() { return knowledgeBase; }
    /** Returns the logical relative path. */
    public String getPath() { return path; }
    /** Returns the media type. */
    public String getContentType() { return contentType; }
    /** Returns a copy of the stored bytes. */
    public byte[] getContent() { return content.clone(); }
    /** Returns when the resource was stored. */
    public Instant getCreatedAt() { return createdAt; }
}
