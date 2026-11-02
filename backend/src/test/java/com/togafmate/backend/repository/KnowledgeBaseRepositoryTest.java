package com.togafmate.backend.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import com.togafmate.backend.entity.KnowledgeBase;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class KnowledgeBaseRepositoryTest {
    @Autowired
    private KnowledgeBaseRepository knowledgeBaseRepository;

    @Test
    void should_treatSqlLikeSearchTextAsLiteral_whenSearchingMetadata() {
        knowledgeBaseRepository.save(new KnowledgeBase("Architecture", "1.0", "Reference material",
                "methodology", "architecture-v1"));

        assertThat(knowledgeBaseRepository.findByTitleContainingIgnoreCaseOrDescriptionContainingIgnoreCase(
                "%' OR '1'='1", "%' OR '1'='1")).isEmpty();
    }
}
