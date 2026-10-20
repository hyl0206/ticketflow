package com.ticketflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.transaction.BeforeTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real MySQL + MVC + bearer authentication; all fixtures roll back after each test. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = TicketServiceTestSupport.TestDatabaseInitializer.class)
@Transactional
class AuthDatabaseIntegrationTest {
    private static final String PASSWORD = "AuthTest123!";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private DataSource dataSource;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private SqlSessionTemplate sqlSession;
    private String username;

    @BeforeTransaction
    void verifyDatabaseAndInitializeSchema() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertEquals("ticketflow_test", connection.getCatalog());
            try (var statement = connection.createStatement();
                 var result = statement.executeQuery("SELECT DATABASE(), CURRENT_USER()")) {
                assertTrue(result.next());
                assertEquals("ticketflow_test", result.getString(1));
                assertTrue(result.getString(2).startsWith("ticketflow_test_user@"));
            }
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                    new ClassPathResource("db/schema-test.sql"));
            populator.setSqlScriptEncoding("UTF-8");
            populator.populate(connection);
        }
    }

    @BeforeEach
    void uniqueUser() {
        username = "auth_" + UUID.randomUUID().toString().replace("-", "");
    }

    @Test
    void registerLoginAndMeUseRealDatabaseAndBcrypt() throws Exception {
        long userId = register();
        String storedHash = jdbc.queryForObject("SELECT password FROM sys_user WHERE id = ?", String.class, userId);
        assertNotEquals(PASSWORD, storedHash);
        assertTrue(passwordEncoder.matches(PASSWORD, storedHash));
        assertEquals("USER", jdbc.queryForObject(
                "SELECT r.role_code FROM sys_role r JOIN sys_user_role ur ON ur.role_id = r.id WHERE ur.user_id = ?",
                String.class, userId));
        String token = login();
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(userId))
                .andExpect(jsonPath("$.data.username").value(username))
                .andExpect(jsonPath("$.data.roles[0]").value("USER"))
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test
    void duplicateUsernameReturnsConflictWithoutExtraUserOrRoleBinding() throws Exception {
        long userId = register();
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("USERNAME_TAKEN"));
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE username = ?", Long.class, username));
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_role WHERE user_id = ?", Long.class, userId));
    }

    @Test
    void adminRoleInjectionIsRejectedWithoutCreatingUser() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "username", username, "password", PASSWORD, "roles", new String[]{"ADMIN"}))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST_BODY"));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE username = ?", Long.class, username));
    }

    @Test
    void disablingUserRejectsAlreadyIssuedTokenAndFurtherLogin() throws Exception {
        long userId = register();
        String token = login();
        jdbc.update("UPDATE sys_user SET status = 0 WHERE id = ?", userId);
        clearRequestCache();
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(credentials()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
    }

    @Test
    void removingUserRoleRevokesPermissionsForAlreadyIssuedToken() throws Exception {
        long userId = register();
        String token = login();
        jdbc.update("DELETE FROM sys_user_role WHERE user_id = ?", userId);
        clearRequestCache();
        assertNoTicketPermission(token);
    }

    @Test
    void disablingRoleRevokesPermissionsForAlreadyIssuedToken() throws Exception {
        register();
        String token = login();
        // This change is inside the test transaction and is rolled back afterwards.
        jdbc.update("UPDATE sys_role SET status = 0 WHERE role_code = 'USER'");
        clearRequestCache();
        assertNoTicketPermission(token);
    }

    @Test
    void registrationWithDisabledDefaultRoleFailsWithoutPartialUser() throws Exception {
        jdbc.update("UPDATE sys_role SET status = 0 WHERE role_code = 'USER'");
        clearRequestCache();
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(credentials()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("REGISTRATION_UNAVAILABLE"));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE username = ?", Long.class, username));
    }

    private long register() throws Exception {
        String response = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.roles[0]").value("USER"))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).path("data").path("id").asLong();
    }

    private String login() throws Exception {
        String response = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").value(3600))
                .andReturn().getResponse().getContentAsString();
        JsonNode payload = json.readTree(response).path("data");
        assertFalse(payload.path("accessToken").asText().isBlank());
        return payload.path("accessToken").asText();
    }

    private String credentials() throws Exception {
        return json.writeValueAsString(Map.of("username", username, "password", PASSWORD));
    }

    private void assertNoTicketPermission(String token) throws Exception {
        // The account is still authenticated; removing a role revokes authorization, not identity.
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles").isEmpty());
        mvc.perform(get("/api/tickets").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    private void clearRequestCache() {
        // A normal HTTP request has a new mapper session; this test wraps several in one transaction.
        sqlSession.clearCache();
    }
}
