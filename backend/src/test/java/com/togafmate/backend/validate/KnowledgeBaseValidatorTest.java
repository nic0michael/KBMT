package com.togafmate.backend.validate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.togafmate.backend.entity.KnowledgeBase;
import com.togafmate.backend.repository.KnowledgeBaseRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeBaseValidatorTest {
    @Mock
    private KnowledgeBaseRepository knowledgeBaseRepository;

    @InjectMocks
    private KnowledgeBaseValidator validator;

    @Test
    void should_returnNoViolations_whenKnowledgeBaseHasValidRequiredMetadata() {
        KnowledgeBase knowledgeBase = new KnowledgeBase("Architecture", "1.0", null, "Free",
                "Architecture_Guide_v1.0");

        assertThat(validator.validate(knowledgeBase)).isEmpty();
    }

    @Test
    void should_rejectMissingRequiredFieldsAndUnsafeStorageIdentifier_whenMetadataIsInvalid() {
        KnowledgeBase knowledgeBase = new KnowledgeBase(" ", "", null, "Free", "../outside");

        assertThat(validator.validate(knowledgeBase))
                .contains("title is required", "version is required", "folder_name_version is invalid");
    }

    @Test
    void should_rejectUnsupportedContentType_whenMetadataIsInvalid() {
        KnowledgeBase knowledgeBase = new KnowledgeBase("Architecture", "1.0", null, "methodology",
                "Architecture_Guide_v1.0");

        assertThat(validator.validate(knowledgeBase)).contains("content type is invalid");
    }

    @Test
    void should_allowBlankOptionalEmail_whenMetadataIsValid() {
        KnowledgeBase knowledgeBase = new KnowledgeBase("Architecture", "1.0", null, "Free",
                "Architecture_Guide_v1.0");

        assertThat(validator.validate(knowledgeBase)).isEmpty();
    }

    @Test
    void should_rejectInvalidOptionalEmail_whenMetadataIsInvalid() {
        KnowledgeBase knowledgeBase = new KnowledgeBase("Architecture", "Architecture_Guide_v1.0", "1.0",
                "Team", "not-an-email", null, null, "Free");

        assertThat(validator.validate(knowledgeBase)).contains("email_address is invalid");
    }

    @Test
    void should_rejectRegisteredFolderIdentifier_whenMetadataIsDuplicated() {
        KnowledgeBase knowledgeBase = new KnowledgeBase("Architecture", "Architecture_Guide_v1.0", "1.0",
                null, null, null, null, "Free");
        when(knowledgeBaseRepository.existsByFolderNameVersion("Architecture_Guide_v1.0")).thenReturn(true);

        assertThat(validator.validate(knowledgeBase)).contains("folder_name_version is already registered");
    }
}
