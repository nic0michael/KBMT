package com.togafmate.backend.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import com.togafmate.backend.entity.User;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class UserRepositoryTest {
    @Autowired
    private UserRepository userRepository;

    @Test
    void should_treatLogonUserIdAsLiteral_whenQueryContainsSqlSyntax() {
        userRepository.save(new User("known-user", "encoded-password"));

        assertThat(userRepository.findForLogon("' OR '1'='1")).isEmpty();
    }
}
