package com.togafmate.backend.validate;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.togafmate.backend.entity.KnowledgeBase;
import com.togafmate.backend.repository.KnowledgeBaseRepository;

/** Validates Knowledge Base metadata against the application requirements. */
@Component
public class KnowledgeBaseValidator {
    private static final Set<String> CONTENT_TYPES = Set.of("Free", "Open-source", "Copyrighted");
    private final KnowledgeBaseRepository knowledgeBaseRepository;

    /** Creates the validator with access to registered folder identifiers. */
    public KnowledgeBaseValidator(KnowledgeBaseRepository knowledgeBaseRepository) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
    }

    /** Returns validation errors for the supplied Knowledge Base metadata. */
    public List<String> validate(KnowledgeBase knowledgeBase) {
        return validate(knowledgeBase, null);
    }

    /** Returns validation errors while allowing the supplied Knowledge Base to retain its folder identifier. */
    public List<String> validate(KnowledgeBase knowledgeBase, UUID excludedId) {
        List<String> errors = new ArrayList<>();
        if (knowledgeBase == null) {
            errors.add("Knowledge Base is required");
            return errors;
        }

        if (isBlank(knowledgeBase.getTitle())) {
            errors.add("title is required");
        }
        if (isBlank(knowledgeBase.getVersion())) {
            errors.add("version is required");
        }
        String folderNameVersion = knowledgeBase.getFolderNameVersion();
        if (!isSafeFolderIdentifier(folderNameVersion)) {
            errors.add("folder_name_version is invalid");
        } else if (hasDuplicateFolderIdentifier(folderNameVersion, excludedId)) {
            errors.add("folder_name_version is already registered");
        }
        if (!isValidContentType(knowledgeBase.getContentType())) {
            errors.add("content type is invalid");
        }
        if (knowledgeBase.getIsActive() == null) {
            errors.add("isActive is required");
        }
        if (!isValidOptionalEmail(knowledgeBase.getEmailAddress())) {
            errors.add("email_address is invalid");
        }
        return errors;
    }

    private boolean hasDuplicateFolderIdentifier(String folderIdentifier, UUID excludedId) {
        return excludedId == null
                ? knowledgeBaseRepository.existsByFolderNameVersion(folderIdentifier)
                : knowledgeBaseRepository.existsByFolderNameVersionAndIdNot(folderIdentifier, excludedId);
    }

    private boolean isSafeFolderIdentifier(String folderIdentifier) {
        return !isBlank(folderIdentifier)
                && folderIdentifier.equals(folderIdentifier.trim())
                && !folderIdentifier.equals(".")
                && !folderIdentifier.equals("..")
                && !folderIdentifier.endsWith(".")
                && folderIdentifier.matches("[^<>:\"/\\\\|?*\\p{Cntrl}]+");
    }

    private boolean isValidContentType(String contentType) {
        return contentType != null
                && CONTENT_TYPES.stream().anyMatch(allowed -> allowed.equalsIgnoreCase(contentType.trim()));
    }

    private boolean isValidOptionalEmail(String emailAddress) {
        return isBlank(emailAddress) || emailAddress.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
