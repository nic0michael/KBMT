package com.togafmate.backend.controller;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.togafmate.backend.service.KnowledgeBaseService;
import com.togafmate.backend.service.KnowledgeBasePageService;
import com.togafmate.backend.service.KnowledgeBaseContentService;
import com.togafmate.backend.controller.KnowledgeBaseFileResponse;
import com.togafmate.backend.entity.KnowledgeBase;
import com.togafmate.backend.validate.KnowledgeBaseValidator;
import org.springframework.web.server.ResponseStatusException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

/** Exposes Knowledge Base metadata endpoints. */
@RestController
@RequestMapping("/api/knowledge-bases")
public class KnowledgeBaseController {
    private static final Logger LOGGER = LoggerFactory.getLogger(KnowledgeBaseController.class);

    private final KnowledgeBaseService knowledgeBaseService;
    private final KnowledgeBaseValidator knowledgeBaseValidator;
    private final KnowledgeBasePageService pageService;
    private final KnowledgeBaseContentService contentService;
    private final int maxFileSizeBytes;

    /** Creates the controller with its application service. */
    public KnowledgeBaseController(KnowledgeBaseService knowledgeBaseService,
            KnowledgeBaseValidator knowledgeBaseValidator,
            KnowledgeBasePageService pageService,
            KnowledgeBaseContentService contentService,
            @Value("${app.knowledge-base.max-file-size-bytes:102400}") int maxFileSizeBytes) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.knowledgeBaseValidator = knowledgeBaseValidator;
        this.pageService = pageService;
        this.contentService = contentService;
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    /** Stores an approved content file at its logical Knowledge Base path. */
    @PostMapping(value = "/{id}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload Knowledge Base file",
            description = "Stores Markdown, HTML, or root/nested meta.json in PostgreSQL. Logical paths are relative; "
                    + "each file is limited by app.knowledge-base.max-file-size-bytes (100 KB by default).")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "201", description = "Knowledge Base file stored successfully")
    @ApiResponse(responseCode = "400", description = "Invalid path, file type, or file size")
    @ApiResponse(responseCode = "403", description = "Administrator permission is required")
    public ResponseEntity<KnowledgeBaseFileResponse> uploadFile(@PathVariable UUID id,
            @RequestParam("path") String path, @RequestParam("file") MultipartFile file) {
        try {
            if (file.getSize() > maxFileSizeBytes) {
                throw new IllegalArgumentException("File exceeds the " + maxFileSizeBytes + " byte limit");
            }
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(contentService.upload(id, path, file.getBytes()));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (java.io.IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read uploaded file", exception);
        }
    }

    /** Lists stored logical file paths for an active Knowledge Base. */
    @GetMapping("/{id}/files")
    @SecurityRequirement(name = "bearerAuth")
    public List<KnowledgeBaseFileResponse> getFiles(@PathVariable UUID id) {
        try {
            return contentService.list(id);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        }
    }

    /** Returns file bytes from PostgreSQL, sandboxing HTML in browser document contexts. */
    @GetMapping(value = "/{id}/files/content")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<byte[]> getFileContent(@PathVariable UUID id, @RequestParam("path") String path) {
        try {
            var file = contentService.content(id, path);
            ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(file.contentType()))
                    .header("X-Content-Type-Options", "nosniff");
            if ("text/html".equals(file.contentType())) {
                response.header("Content-Security-Policy", "sandbox; default-src 'none'; style-src 'unsafe-inline'");
            }
            return response.body(file.bytes());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        }
    }

    /** Imports and validates a Knowledge Base ZIP. */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import Knowledge Base ZIP",
            description = "Imports a ZIP containing root README.md and meta.json. ZIP size, entry count, and total "
                    + "content have no application limit; each allowed content entry has the configured per-file limit.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "201", description = "Knowledge Base ZIP imported successfully")
    @ApiResponse(responseCode = "400", description = "Invalid archive, duplicate title, or invalid metadata")
    @ApiResponse(responseCode = "403", description = "Administrator permission is required")
    public ResponseEntity<KnowledgeBaseResponse> importZip(@RequestParam("file") MultipartFile file,
            @Parameter(description = "Replace a Knowledge Base with the same title when its version differs")
            @RequestParam(defaultValue = "false") boolean overwrite) {
        LOGGER.info("Starting Knowledge Base ZIP import: fileSizeBytes={}, overwrite={}", file.getSize(), overwrite);
        try {
            KnowledgeBase imported = contentService.importZip(file::getInputStream, overwrite);
            LOGGER.info("Imported Knowledge Base ZIP into PostgreSQL: id={}, title={}, version={}",
                    imported.getId(), imported.getTitle(), imported.getVersion());
            return ResponseEntity.status(HttpStatus.CREATED).body(new KnowledgeBaseResponse(imported.getId(),
                    imported.getTitle(), imported.getVersion(), imported.getDescription(), imported.getContentType(),
                    imported.getFolderNameVersion(), imported.getAuthor(), imported.getEmailAddress(),
                    imported.getTableOfContents(), imported.getIsActive()));
        } catch (IllegalArgumentException exception) {
            LOGGER.error("Knowledge Base ZIP import failed: overwrite={}", overwrite, exception);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (RuntimeException exception) {
            LOGGER.error("Database or server error during Knowledge Base ZIP import: overwrite={}", overwrite,
                    exception);
            throw exception;
        }
    }

    /** Exports a Knowledge Base as a generated ZIP archive. */
    @GetMapping(value = "/{id}/export", produces = "application/zip")
    @Operation(summary = "Export Knowledge Base ZIP",
            description = "Streams README.md, meta.json, and stored Knowledge Base files from PostgreSQL.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "404", description = "Knowledge Base was not found")
    @ApiResponse(responseCode = "403", description = "Administrator permission is required")
    public ResponseEntity<StreamingResponseBody> exportZip(@PathVariable UUID id) {
        try {
            contentService.validateExport(id);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=knowledge-base-" + id + ".zip")
                    .contentType(MediaType.parseMediaType("application/zip"))
                    .body(output -> contentService.writeExportZip(id, output));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        }
    }

    /** Stores an administrator-uploaded Markdown page in PostgreSQL. */
    @PostMapping(value = "/{id}/pages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "201", description = "Markdown page uploaded successfully")
    public ResponseEntity<KnowledgeBasePageResponse> uploadPage(@PathVariable UUID id,
            @RequestParam("file") MultipartFile file) {
        try {
            if (file.getSize() > maxFileSizeBytes) {
                throw new IllegalArgumentException("File exceeds the " + maxFileSizeBytes + " byte limit");
            }
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(pageService.upload(id, file.getOriginalFilename(), file.getBytes()));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (java.io.IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read uploaded Markdown file", exception);
        }
    }

    /** Lists Markdown pages belonging to an active Knowledge Base. */
    @GetMapping("/{id}/pages")
    @SecurityRequirement(name = "bearerAuth")
    public List<KnowledgeBasePageResponse> getPages(@PathVariable UUID id) {
        try {
            return pageService.list(id);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        }
    }

    /** Returns stored Markdown source for a page in an active Knowledge Base. */
    @GetMapping(value = "/{id}/pages/{pageId}", produces = MediaType.TEXT_PLAIN_VALUE)
    @SecurityRequirement(name = "bearerAuth")
    public String getPageContent(@PathVariable UUID id, @PathVariable UUID pageId) {
        try {
            return pageService.content(id, pageId);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        }
    }

    /** Returns all registered Knowledge Bases. */
    @GetMapping
    @SecurityRequirement(name = "bearerAuth")
    public List<KnowledgeBaseResponse> getKnowledgeBases() {
        return knowledgeBaseService.findAll();
    }

    /** Returns active and inactive Knowledge Bases for administrator management. */
    @GetMapping("/management")
    @SecurityRequirement(name = "bearerAuth")
    public List<KnowledgeBaseResponse> getKnowledgeBasesForManagement() {
        return knowledgeBaseService.findAllForManagement();
    }

    /** Validates and registers Knowledge Base metadata. */
    @PostMapping
    @Operation(summary = "Create Knowledge Base", description = "Creates Knowledge Base metadata. The API generates the UUID; do not supply an ID.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "201", description = "Knowledge Base created successfully")
    public ResponseEntity<KnowledgeBaseResponse> createKnowledgeBase(@RequestBody KnowledgeBaseRequest request) {
        KnowledgeBase knowledgeBase = new KnowledgeBase(request.title(), request.folderNameVersion(),
                request.version(), request.author(), request.emailAddress(), request.tableOfContents(),
                request.description(), request.contentType(), request.isActive());
        List<String> errors = knowledgeBaseValidator.validate(knowledgeBase);
        if (!errors.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join("; ", errors));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(knowledgeBaseService.save(knowledgeBase));
    }

    /** Validates and updates metadata for an existing Knowledge Base. */
    @PutMapping("/{id}")
    @Operation(summary = "Update Knowledge Base", description = "Updates existing metadata. Supply the existing Knowledge Base UUID in the path.")
    @SecurityRequirement(name = "bearerAuth")
    public KnowledgeBaseResponse updateKnowledgeBase(@PathVariable UUID id,
            @RequestBody KnowledgeBaseRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Knowledge Base metadata is required");
        }
        KnowledgeBase metadata = new KnowledgeBase(request.title(), request.folderNameVersion(),
                request.version(), request.author(), request.emailAddress(), request.tableOfContents(),
                request.description(), request.contentType(), request.isActive());
        List<String> errors = knowledgeBaseValidator.validate(metadata, id);
        if (!errors.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join("; ", errors));
        }
        try {
            return knowledgeBaseService.update(id, metadata);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        }
    }
}
