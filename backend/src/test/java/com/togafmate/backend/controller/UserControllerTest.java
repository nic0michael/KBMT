package com.togafmate.backend.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import jakarta.servlet.http.Cookie;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import com.togafmate.backend.entity.User;
import com.togafmate.backend.entity.UserPermission;
import com.togafmate.backend.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UserControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void clearUsers() {
        userRepository.deleteAll();
    }

    @Test
    void should_registerAndLogonWithoutReturningPassword_whenCredentialsAreValid() throws Exception {
        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userID\":\"reader1\",\"password\":\"secret\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.permissions[0]").value("NO_ACCESS"))
                .andExpect(jsonPath("$.password").doesNotExist());

        assertThat(userRepository.findById("reader1").orElseThrow().getPassword()).isNotEqualTo("secret");

        mockMvc.perform(post("/api/users/logon")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userID\":\"reader1\",\"password\":\"secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userID").value("reader1"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void should_logInUsingLoginEndpoint_whenCredentialsAreValid() throws Exception {
        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userID\":\"reader1\",\"password\":\"secret\"}"));

        mockMvc.perform(post("/api/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userID\":\"reader1\",\"password\":\"secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userID").value("reader1"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void should_issueBearerTokenUsableForProtectedRequests_whenAdministratorLogsIn() throws Exception {
        userRepository.save(new User("admin", passwordEncoder.encode("secret"),
                List.of(UserPermission.ADMINISTRATOR_USER_ACCESS)));

        String response = mockMvc.perform(post("/api/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userID\":\"admin\",\"password\":\"secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn().getResponse().getContentAsString();
        String token = com.jayway.jsonpath.JsonPath.read(response, "$.accessToken");

        mockMvc.perform(get("/api/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userID").value("admin"));
    }

    @Test
    void should_authorizeSwaggerRequestsUsingLoginCookie_whenAdministratorLogsIn() throws Exception {
        userRepository.save(new User("admin", passwordEncoder.encode("secret"),
                List.of(UserPermission.ADMINISTRATOR_USER_ACCESS)));

        var loginResponse = mockMvc.perform(post("/api/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userID\":\"admin\",\"password\":\"secret\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse();
        Cookie accessCookie = loginResponse.getCookie("TOGAFMATE_ACCESS_TOKEN");

        assertThat(accessCookie).isNotNull();
        assertThat(accessCookie.isHttpOnly()).isTrue();
        assertThat(accessCookie.getMaxAge()).isEqualTo(900);

        mockMvc.perform(get("/api/users").cookie(accessCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userID").value("admin"));
    }

    @Test
    void should_clearAccessCookie_whenUserLogsOut() throws Exception {
        var logoutResponse = mockMvc.perform(post("/api/users/logout"))
                .andExpect(status().isNoContent())
                .andReturn().getResponse();

        Cookie accessCookie = logoutResponse.getCookie("TOGAFMATE_ACCESS_TOKEN");
        assertThat(accessCookie).isNotNull();
        assertThat(accessCookie.isHttpOnly()).isTrue();
        assertThat(accessCookie.getMaxAge()).isZero();
    }

    @Test
    void should_rejectLogon_whenPasswordIsIncorrect() throws Exception {
        mockMvc.perform(post("/api/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userID\":\"reader1\",\"password\":\"secret\"}"));

        mockMvc.perform(post("/api/users/logon")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userID\":\"reader1\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void should_listUsersWithoutPasswords_whenAdministratorRequestsUsers() throws Exception {
        userRepository.save(new User("reader1", passwordEncoder.encode("secret"),
                List.of(UserPermission.USER_ACCESS)));

        mockMvc.perform(get("/api/users").with(user("admin").authorities(() -> "ADMINISTRATOR_USER_ACCESS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userID").value("reader1"))
                .andExpect(jsonPath("$[0].permissions[0]").value("USER_ACCESS"))
                .andExpect(jsonPath("$[0].password").doesNotExist());
    }

    @Test
    void should_forbidUserListing_whenRequesterIsNotAdministrator() throws Exception {
        mockMvc.perform(get("/api/users").with(user("reader").authorities(() -> "USER_ACCESS")))
                .andExpect(status().isForbidden());
    }

    @Test
    void should_changeUserIdAndPassword_whenAdministratorRequestsUpdate() throws Exception {
        userRepository.save(new User("reader1", passwordEncoder.encode("old-secret"),
                List.of(UserPermission.USER_ACCESS)));

        mockMvc.perform(put("/api/users/reader1")
                        .with(user("admin").authorities(() -> "ADMINISTRATOR_USER_ACCESS"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userID\":\"reader2\",\"password\":\"new-secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userID").value("reader2"))
                .andExpect(jsonPath("$.permissions[0]").value("USER_ACCESS"))
                .andExpect(jsonPath("$.password").doesNotExist());

        assertThat(userRepository.findById("reader1")).isEmpty();
        String updatedPassword = userRepository.findById("reader2").orElseThrow().getPassword();
        assertThat(passwordEncoder.matches("new-secret", updatedPassword)).isTrue();
        assertThat(updatedPassword).isNotEqualTo("new-secret");
    }

    @Test
    void should_preservePasswordAndPermissions_whenAdministratorChangesUserIdOnly() throws Exception {
        String encodedPassword = passwordEncoder.encode("existing-secret");
        userRepository.save(new User("reader1", encodedPassword, List.of(UserPermission.USER_ACCESS)));

        mockMvc.perform(put("/api/users/reader1")
                        .with(user("admin").authorities(() -> "ADMINISTRATOR_USER_ACCESS"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userID\":\"reader2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userID").value("reader2"))
                .andExpect(jsonPath("$.permissions[0]").value("USER_ACCESS"));

        assertThat(userRepository.findById("reader1")).isEmpty();
        assertThat(userRepository.findById("reader2").orElseThrow().getPassword()).isEqualTo(encodedPassword);
    }

    @Test
    void should_forbidUserPasswordUpdate_whenRequesterIsNotAdministrator() throws Exception {
        mockMvc.perform(put("/api/users/reader1")
                        .with(user("reader").authorities(() -> "USER_ACCESS"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userID\":\"reader1\",\"password\":\"new-secret\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void should_rejectUserIdChange_whenTargetIdAlreadyExists() throws Exception {
        userRepository.save(new User("reader1", passwordEncoder.encode("secret")));
        userRepository.save(new User("reader2", passwordEncoder.encode("secret")));

        mockMvc.perform(put("/api/users/reader1")
                        .with(user("admin").authorities(() -> "ADMINISTRATOR_USER_ACCESS"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userID\":\"reader2\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void should_requireAdministrator_whenChangingPermissions() throws Exception {
        userRepository.save(new User("reader1", "encoded-password"));

        mockMvc.perform(put("/api/users/reader1/permissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permission\":\"USER_ACCESS\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = "ADMINISTRATOR_USER_ACCESS")
    void should_addPermission_whenAdministratorRequestsChange() throws Exception {
        userRepository.save(new User("reader1", "encoded-password"));

        mockMvc.perform(put("/api/users/reader1/permissions")
                        .with(user("admin").authorities(() -> "ADMINISTRATOR_USER_ACCESS"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permission\":\"USER_ACCESS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions[0]").value("USER_ACCESS"));
    }

    @Test
    void should_removePermissionAndRestoreNoAccess_whenLastPermissionIsRemoved() throws Exception {
        userRepository.save(new User("reader1", "encoded-password", List.of(UserPermission.USER_ACCESS)));

        mockMvc.perform(delete("/api/users/reader1/permissions")
                        .with(user("admin").authorities(() -> "ADMINISTRATOR_USER_ACCESS"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permission\":\"USER_ACCESS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions[0]").value("NO_ACCESS"));
    }

    @Test
    void should_denyKnowledgeBaseAccess_whenUserHasNoAccessPermission() throws Exception {
        userRepository.save(new User("reader1", passwordEncoder.encode("secret")));

        mockMvc.perform(get("/api/knowledge-bases").with(jwt().authorities(() -> "NO_ACCESS")))
                .andExpect(status().isForbidden());
    }
}
