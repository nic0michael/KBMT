package com.togafmate.backend.service;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.togafmate.backend.controller.UserResponse;
import com.togafmate.backend.entity.User;
import com.togafmate.backend.entity.UserPermission;
import com.togafmate.backend.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    void should_storeEncodedPasswordAndNoAccess_whenRegisteringUser() {
        when(userRepository.existsById("reader1")).thenReturn(false);
        when(passwordEncoder.encode("secret")).thenReturn("encoded-secret");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse result = userService.register("reader1", "secret");

        assertThat(result.userID()).isEqualTo("reader1");
        assertThat(result.permissions()).containsExactly(UserPermission.NO_ACCESS);
        verify(userRepository).save(org.mockito.ArgumentMatchers.argThat(user ->
                user.getPassword().equals("encoded-secret")));
    }

    @Test
    void should_returnNoUser_whenLogonPasswordDoesNotMatch() {
        User user = new User("reader1", "encoded-secret");
        when(userRepository.findForLogon("reader1")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "encoded-secret")).thenReturn(false);

        assertThat(userService.logon("reader1", "wrong")).isEmpty();
    }
}
