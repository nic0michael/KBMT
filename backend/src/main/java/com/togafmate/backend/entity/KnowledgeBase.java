package com.togafmate.backend.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Stores metadata that identifies a Knowledge Base. */
@Entity
@Table(name = "knowledge_bases")
public class KnowledgeBase {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String version;

    private String description;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "folder_name_version", nullable = false, unique = true)
    private String folderNameVersion;

    private String author;

    @Column(name = "email_address")
    private String emailAddress;

    @Column(name = "table_of_contents")
    private String tableOfContents;

    @Column(name = "knowledge_base_is_active")
    private Boolean isActive;

    protected KnowledgeBase() {
    }

    /** Creates metadata for a Knowledge Base. */
    public KnowledgeBase(String title, String version, String description, String contentType,
            String folderNameVersion) {
        this(title, folderNameVersion, version, null, null, null, description, contentType, true);
    }

    /** Creates Knowledge Base metadata using the fields defined by the project specification. */
    public KnowledgeBase(String title, String folderNameVersion, String version, String author,
            String emailAddress, String tableOfContents, String description, String contentType) {
        this(title, folderNameVersion, version, author, emailAddress, tableOfContents, description, contentType, true);
    }

    /** Creates Knowledge Base metadata with an explicit active state. */
    public KnowledgeBase(String title, String folderNameVersion, String version, String author,
            String emailAddress, String tableOfContents, String description, String contentType, Boolean isActive) {
        this.title = title;
        this.folderNameVersion = folderNameVersion;
        this.version = version;
        this.author = author;
        this.emailAddress = emailAddress;
        this.tableOfContents = tableOfContents;
        this.description = description;
        this.contentType = contentType;
        this.isActive = isActive;
    }

    /** Returns the database identifier. */
    public UUID getId() {
        return id;
    }

    /** Returns the Knowledge Base title. */
    public String getTitle() {
        return title;
    }

    /** Returns the Knowledge Base version. */
    public String getVersion() {
        return version;
    }

    /** Returns the Knowledge Base description. */
    public String getDescription() {
        return description;
    }

    /** Returns the Knowledge Base content type. */
    public String getContentType() {
        return contentType;
    }

    /** Returns the identifier of the Knowledge Base folder. */
    public String getFolderNameVersion() {
        return folderNameVersion;
    }

    /** Returns the optional author. */
    public String getAuthor() {
        return author;
    }

    /** Returns the optional author email address. */
    public String getEmailAddress() {
        return emailAddress;
    }

    /** Returns the optional Table of Contents content or resource reference. */
    public String getTableOfContents() {
        return tableOfContents;
    }

    /** Returns whether this Knowledge Base is active. */
    public Boolean getIsActive() {
        return isActive;
    }

    /** Replaces editable Knowledge Base metadata while preserving its database identifier. */
    public void updateMetadata(KnowledgeBase metadata) {
        title = metadata.title;
        version = metadata.version;
        description = metadata.description;
        contentType = metadata.contentType;
        folderNameVersion = metadata.folderNameVersion;
        author = metadata.author;
        emailAddress = metadata.emailAddress;
        tableOfContents = metadata.tableOfContents;
        isActive = metadata.isActive;
    }
}
