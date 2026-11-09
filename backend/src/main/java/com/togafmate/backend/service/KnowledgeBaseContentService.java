package com.togafmate.backend.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.togafmate.backend.controller.KnowledgeBaseFileResponse;
import com.togafmate.backend.controller.KnowledgeBaseFileContent;
import com.togafmate.backend.entity.KnowledgeBase;
import com.togafmate.backend.entity.KnowledgeBaseFile;
import com.togafmate.backend.repository.KnowledgeBaseFileRepository;
import com.togafmate.backend.repository.KnowledgeBaseRepository;
import com.togafmate.backend.repository.KnowledgeBasePageRepository;
import com.togafmate.backend.entity.KnowledgeBasePage;
import com.togafmate.backend.validate.KnowledgeBaseValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Stores, imports, and exports Knowledge Base files using PostgreSQL as the persistent store. */
@Service
public class KnowledgeBaseContentService {
    private static final Logger LOGGER = LoggerFactory.getLogger(KnowledgeBaseContentService.class);
    private static final String README_PATH = "README.md";
    private static final String META_PATH = "meta.json";
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final KnowledgeBaseFileRepository fileRepository;
    private final KnowledgeBasePageRepository pageRepository;
    private final KnowledgeBaseValidator validator;
    private final ObjectMapper objectMapper;
    private final int maxFileSizeBytes;

    /** Creates the service with database repositories and the configurable per-file limit. */
    public KnowledgeBaseContentService(KnowledgeBaseRepository knowledgeBaseRepository,
            KnowledgeBaseFileRepository fileRepository, KnowledgeBasePageRepository pageRepository,
            KnowledgeBaseValidator validator, ObjectMapper objectMapper,
            @Value("${app.knowledge-base.max-file-size-bytes:102400}") int maxFileSizeBytes) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.fileRepository = fileRepository;
        this.pageRepository = pageRepository;
        this.validator = validator;
        this.objectMapper = objectMapper;
        if (maxFileSizeBytes < 1) {
            throw new IllegalArgumentException("Knowledge Base file size limit must be positive");
        }
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    /** Validates and stores an allowed file, activating the Knowledge Base on success. */
    @Transactional
    public KnowledgeBaseFileResponse upload(UUID knowledgeBaseId, String path, byte[] content) {
        String safePath = validatePath(path);
        validateContent(safePath, content);
        KnowledgeBase knowledgeBase = knowledgeBaseRepository.findById(knowledgeBaseId)
                .orElseThrow(() -> new IllegalArgumentException("Knowledge Base was not found"));
        if (META_PATH.equals(safePath)) {
            validateManifestMetadata(parseMetadata(content), knowledgeBase);
        }
        if (fileRepository.existsByKnowledgeBaseIdAndPath(knowledgeBaseId, safePath)) {
            throw new IllegalArgumentException("A file with this logical path already exists");
        }
        if (safePath.indexOf('/') < 0 && safePath.toLowerCase(Locale.ROOT).endsWith(".md")
                && pageRepository.existsByKnowledgeBaseIdAndTitleIgnoreCase(knowledgeBaseId,
                        safePath.substring(0, safePath.length() - 3))) {
            throw new IllegalArgumentException("A Markdown page with this path already exists");
        }
        KnowledgeBaseFile file = fileRepository.save(new KnowledgeBaseFile(knowledgeBase, safePath,
                mediaType(safePath), content));
        if (!Boolean.TRUE.equals(knowledgeBase.getIsActive())) {
            knowledgeBase.updateMetadata(new KnowledgeBase(knowledgeBase.getTitle(),
                    knowledgeBase.getFolderNameVersion(), knowledgeBase.getVersion(), knowledgeBase.getAuthor(),
                    knowledgeBase.getEmailAddress(), knowledgeBase.getTableOfContents(), knowledgeBase.getDescription(),
                    knowledgeBase.getContentType(), true));
        }
        LOGGER.info("Stored Knowledge Base file {} for {}", safePath, knowledgeBaseId);
        return response(file);
    }

    /** Returns logical file metadata for a Knowledge Base. */
    @Transactional(readOnly = true)
    public List<KnowledgeBaseFileResponse> list(UUID knowledgeBaseId) {
        KnowledgeBase knowledgeBase = requireKnowledgeBase(knowledgeBaseId);
        if (!Boolean.TRUE.equals(knowledgeBase.getIsActive())) {
            throw new IllegalArgumentException("Active Knowledge Base was not found");
        }
        List<KnowledgeBaseFileResponse> result = fileRepository.findAllByKnowledgeBaseIdOrderByPathAsc(knowledgeBaseId)
                .stream().map(this::response).toList();
        LOGGER.info("Listed {} Knowledge Base files for {}", result.size(), knowledgeBaseId);
        return result;
    }

    /** Retrieves stored file bytes for an active Knowledge Base. */
    @Transactional(readOnly = true)
    public KnowledgeBaseFileContent content(UUID knowledgeBaseId, String path) {
        KnowledgeBase knowledgeBase = requireKnowledgeBase(knowledgeBaseId);
        if (!Boolean.TRUE.equals(knowledgeBase.getIsActive())) {
            throw new IllegalArgumentException("Active Knowledge Base was not found");
        }
        String safePath = validatePath(path);
        KnowledgeBaseFile file = fileRepository.findByKnowledgeBaseIdAndPath(knowledgeBaseId, safePath)
                .orElseThrow(() -> new IllegalArgumentException("Knowledge Base file was not found"));
        return new KnowledgeBaseFileContent(file.getContentType(), file.getContent());
    }

    /** Imports a validated ZIP atomically, replacing a title match only when explicitly requested. */
    @Transactional
    public KnowledgeBase importZip(ArchiveInputSupplier archiveSupplier, boolean overwrite) {
        return importValidatedZip(archiveSupplier, overwrite);
    }

    private KnowledgeBase importValidatedZip(ArchiveInputSupplier archiveSupplier, boolean overwrite) {
        ArchiveMetadata archiveMetadata = validateArchive(archiveSupplier);
        JsonNode metadata = parseMetadata(archiveMetadata.metadataBytes());
        String title = requiredText(metadata, "title");
        String version = requiredText(metadata, "version");
        String folder = requiredText(metadata, "folder_name_version");
        String contentType = optionalText(metadata, "content_type", "Free");
        KnowledgeBase existing = knowledgeBaseRepository.findByTitleIgnoreCase(title).orElse(null);
        if (existing != null && !overwrite) {
            LOGGER.warn("This is a duplicate record and will not be updated: title={}", title);
            throw new IllegalArgumentException(
                    "A Knowledge Base with this title already exists; select overwrite to replace it");
        }
        if (existing != null && existing.getVersion().equals(version)) {
            throw new IllegalArgumentException("Imported version must differ from the existing Knowledge Base version");
        }
        KnowledgeBase candidate = new KnowledgeBase(title, folder, version,
                optionalText(metadata, "author", null), optionalText(metadata, "email_address", null),
                optionalText(metadata, "Table_Of_Contents", null), optionalText(metadata, "description", null),
                contentType, true);
        List<String> errors = validator.validate(candidate, existing == null ? null : existing.getId());
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", errors));
        }
        KnowledgeBase saved;
        if (existing == null) {
            saved = knowledgeBaseRepository.save(candidate);
        } else {
            existing.updateMetadata(candidate);
            saved = knowledgeBaseRepository.save(existing);
            fileRepository.deleteAllByKnowledgeBaseId(saved.getId());
            pageRepository.deleteAllByKnowledgeBaseId(saved.getId());
        }
        persistArchiveFiles(archiveSupplier, saved);
        return saved;
    }

    /** Confirms the Knowledge Base exists before the HTTP response starts streaming. */
    @Transactional(readOnly = true)
    public void validateExport(UUID knowledgeBaseId) {
        try {
            KnowledgeBase knowledgeBase = requireKnowledgeBase(knowledgeBaseId);
            LOGGER.info("Preparing Knowledge Base ZIP export: id={}, title={}, version={}",
                    knowledgeBaseId, knowledgeBase.getTitle(), knowledgeBase.getVersion());
        } catch (RuntimeException exception) {
            LOGGER.error("Failed to prepare Knowledge Base ZIP export: id={}", knowledgeBaseId, exception);
            throw exception;
        }
    }

    /** Streams a ZIP from PostgreSQL content and generated metadata to the response output. */
    @Transactional(readOnly = true)
    public void writeExportZip(UUID knowledgeBaseId, OutputStream output) throws IOException {
        LOGGER.info("Starting Knowledge Base ZIP export: id={}", knowledgeBaseId);
        try {
            KnowledgeBase knowledgeBase = requireKnowledgeBase(knowledgeBaseId);
            LOGGER.info("Exporting Knowledge Base ZIP: id={}, title={}, version={}",
                    knowledgeBaseId, knowledgeBase.getTitle(), knowledgeBase.getVersion());
            boolean hasReadme = false;
            boolean hasMetadata = false;
            long exportedCount = 0;
            try (ZipOutputStream zip = new ZipOutputStream(output);
                    Stream<KnowledgeBaseFile> files = fileRepository.streamAllByKnowledgeBaseId(knowledgeBaseId)) {
                var iterator = files.iterator();
                while (iterator.hasNext()) {
                    KnowledgeBaseFile file = iterator.next();
                    putEntry(zip, file.getPath(), file.getContent());
                    hasReadme |= README_PATH.equals(file.getPath());
                    hasMetadata |= META_PATH.equals(file.getPath());
                    exportedCount++;
                }
                try (Stream<KnowledgeBasePage> pages = pageRepository.streamAllByKnowledgeBaseId(knowledgeBaseId)) {
                    var pageIterator = pages.iterator();
                    while (pageIterator.hasNext()) {
                        KnowledgeBasePage page = pageIterator.next();
                        String path = page.getTitle() + ".md";
                        if (fileRepository.existsByKnowledgeBaseIdAndPath(knowledgeBaseId, path)) {
                            throw new IOException("Legacy Markdown page conflicts with stored file path " + path);
                        }
                        putEntry(zip, path, page.getContent().getBytes(StandardCharsets.UTF_8));
                        exportedCount++;
                    }
                }
                if (!hasReadme) putEntry(zip, README_PATH, readme(knowledgeBase).getBytes(StandardCharsets.UTF_8));
                if (!hasMetadata) putEntry(zip, META_PATH, metadataJson(knowledgeBase));
                zip.finish();
                zip.flush();
            }
            LOGGER.info("Exported Knowledge Base ZIP successfully: id={}, title={}, version={}, fileCount={}",
                    knowledgeBaseId, knowledgeBase.getTitle(), knowledgeBase.getVersion(), exportedCount);
        } catch (IOException | RuntimeException exception) {
            LOGGER.error("Failed to export Knowledge Base ZIP: id={}", knowledgeBaseId, exception);
            throw exception;
        }
    }

    private ArchiveMetadata validateArchive(ArchiveInputSupplier archiveSupplier) {
        byte[] metadataBytes = null;
        Set<String> paths = new HashSet<>();
        try (ZipInputStream zip = new ZipInputStream(archiveSupplier.open())) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    String directoryPath = entry.getName();
                    validatePath(directoryPath.substring(0, directoryPath.length() - 1));
                    zip.closeEntry();
                    continue;
                }
                String path = validatePath(entry.getName());
                if (!paths.add(path)) {
                    throw new IllegalArgumentException("ZIP contains a duplicate logical path: " + path);
                }
                byte[] content = readEntry(zip, path);
                validateContent(path, content);
                if (META_PATH.equals(path)) metadataBytes = content;
                zip.closeEntry();
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("ZIP archive could not be read", exception);
        }
        if (!paths.contains(README_PATH)) {
            throw new IllegalArgumentException("ZIP must contain root README.md");
        }
        if (metadataBytes == null) {
            throw new IllegalArgumentException("ZIP must contain root meta.json");
        }
        return new ArchiveMetadata(metadataBytes);
    }

    private void persistArchiveFiles(ArchiveInputSupplier archiveSupplier, KnowledgeBase knowledgeBase) {
        try (ZipInputStream zip = new ZipInputStream(archiveSupplier.open())) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    zip.closeEntry();
                    continue;
                }
                String path = validatePath(entry.getName());
                byte[] content = readEntry(zip, path);
                validateContent(path, content);
                fileRepository.save(new KnowledgeBaseFile(knowledgeBase, path, mediaType(path), content));
                zip.closeEntry();
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("ZIP archive could not be read while storing files", exception);
        }
    }

    private byte[] readEntry(ZipInputStream zip, String path) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = zip.read(buffer)) != -1) {
            if ((long) bytes.size() + read > maxFileSizeBytes) {
                throw new IllegalArgumentException("File exceeds the " + maxFileSizeBytes + " byte limit: " + path);
            }
            bytes.write(buffer, 0, read);
        }
        return bytes.toByteArray();
    }

    private JsonNode parseMetadata(byte[] content) {
        try {
            JsonNode metadata = objectMapper.readTree(content);
            if (metadata == null || !metadata.isObject()) {
                throw new IllegalArgumentException("meta.json must contain a JSON object");
            }
            return metadata;
        } catch (IOException exception) {
            throw new IllegalArgumentException("meta.json is not valid JSON", exception);
        }
    }

    private void validateContent(String path, byte[] content) {
        if (content == null || content.length == 0 || content.length > maxFileSizeBytes) {
            throw new IllegalArgumentException("File must be between 1 byte and " + maxFileSizeBytes + " bytes");
        }
        String lowerPath = path.toLowerCase(Locale.ROOT);
        boolean metadataFile = META_PATH.equals(path) || path.endsWith("/" + META_PATH);
        if (!(lowerPath.endsWith(".md") || lowerPath.endsWith(".html") || metadataFile)) {
            throw new IllegalArgumentException("Only Markdown, HTML, and meta.json files are supported");
        }
        if (lowerPath.endsWith(".html") || lowerPath.endsWith(".md") || metadataFile) {
            validateUtf8(content);
        }
        if (metadataFile) {
            parseMetadata(content);
        }
    }

    private void validateUtf8(byte[] content) {
        try {
            StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(content));
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("Content must be valid UTF-8", exception);
        }
    }

    private String validatePath(String path) {
        if (path == null || path.isBlank() || path.length() > 1024 || path.startsWith("/")
                || path.contains("\\") || path.matches("^[A-Za-z]:.*") || path.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("File path is invalid");
        }
        for (String segment : path.split("/", -1)) {
            if (segment.isBlank() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("File path is invalid");
            }
        }
        return path;
    }

    private String mediaType(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".html")) return "text/html";
        if (lower.endsWith(".json")) return "application/json";
        return "text/markdown";
    }

    private KnowledgeBase requireKnowledgeBase(UUID id) {
        return knowledgeBaseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Knowledge Base was not found"));
    }

    private KnowledgeBaseFileResponse response(KnowledgeBaseFile file) {
        return new KnowledgeBaseFileResponse(file.getId(), file.getPath(), file.getContentType(),
                file.getContent().length);
    }

    private String requiredText(JsonNode metadata, String name) {
        String value = optionalText(metadata, name, null);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("meta.json requires " + name);
        return value;
    }

    private void validateManifestMetadata(JsonNode metadata, KnowledgeBase knowledgeBase) {
        if (!knowledgeBase.getTitle().equals(requiredText(metadata, "title"))
                || !knowledgeBase.getVersion().equals(requiredText(metadata, "version"))
                || !knowledgeBase.getFolderNameVersion().equals(requiredText(metadata, "folder_name_version"))) {
            throw new IllegalArgumentException("meta.json must identify the selected Knowledge Base and version");
        }
    }

    private String optionalText(JsonNode metadata, String name, String fallback) {
        JsonNode value = metadata.get(name);
        return value == null || value.isNull() ? fallback : value.asText();
    }

    private byte[] metadataJson(KnowledgeBase kb) throws IOException {
        var metadata = objectMapper.createObjectNode();
        metadata.put("title", kb.getTitle());
        metadata.put("folder_name_version", kb.getFolderNameVersion());
        metadata.put("version", kb.getVersion());
        metadata.put("content_type", kb.getContentType());
        if (kb.getDescription() != null) metadata.put("description", kb.getDescription());
        if (kb.getAuthor() != null) metadata.put("author", kb.getAuthor());
        if (kb.getEmailAddress() != null) metadata.put("email_address", kb.getEmailAddress());
        if (kb.getTableOfContents() != null) metadata.put("Table_Of_Contents", kb.getTableOfContents());
        return objectMapper.writeValueAsBytes(metadata);
    }

    private String readme(KnowledgeBase kb) {
        StringBuilder readme = new StringBuilder("# ").append(kb.getTitle())
                .append("\n\n## Title\n\n").append(kb.getTitle())
                .append("\n\n## Folder Name and Version\n\n").append(kb.getFolderNameVersion())
                .append("\n\n## Version\n\n").append(kb.getVersion());
        appendOptional(readme, "Author (Optional)", kb.getAuthor());
        appendOptional(readme, "Email Address (Optional)", kb.getEmailAddress());
        appendOptional(readme, "Table_Of_Contents (Optional)", kb.getTableOfContents());
        return readme.append('\n').toString();
    }

    private void appendOptional(StringBuilder readme, String label, String value) {
        if (value != null && !value.isBlank()) {
            readme.append("\n\n## ").append(label).append("\n\n").append(value);
        }
    }

    private void putEntry(ZipOutputStream zip, String path, byte[] content) throws IOException {
        zip.putNextEntry(new ZipEntry(path));
        zip.write(content);
        zip.closeEntry();
    }

    private record ArchiveMetadata(byte[] metadataBytes) { }
}
