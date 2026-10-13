package com.ticketflow;

import com.ticketflow.dto.TicketCreateRequest;
import com.ticketflow.entity.User;
import com.ticketflow.enums.TicketPriority;
import com.ticketflow.mapper.TicketMapper;
import com.ticketflow.mapper.UserMapper;
import com.ticketflow.service.TicketService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.net.URI;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockingDetails;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@ContextConfiguration(initializers = TicketServiceTestSupport.TestDatabaseInitializer.class)
abstract class TicketServiceTestSupport {

    @Autowired protected TicketService ticketService;
    @Autowired protected TicketMapper ticketMapper;
    @Autowired protected UserMapper userMapper;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired private DataSource dataSource;

    protected User creator;
    protected String marker;

    /** Runs before datasource creation, so missing/unsafe settings cannot fall back to development. */
    public static class TestDatabaseInitializer
            implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext context) {
            String url = required("TICKETFLOW_TEST_DB_URL");
            String username = required("TICKETFLOW_TEST_DB_USERNAME");
            String password = required("TICKETFLOW_TEST_DB_PASSWORD");
            validateUrl(url);
            if (!"ticketflow_test_user".equals(username)) {
                throw new IllegalStateException("Tests require the ticketflow_test_user account");
            }
            TestPropertyValues.of(
                    "spring.datasource.url=" + url,
                    "spring.datasource.username=" + username,
                    "spring.datasource.password=" + password,
                    "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
                    "spring.sql.init.mode=never"
            ).applyTo(context.getEnvironment());
        }

        private static String required(String name) {
            String value = System.getenv(name);
            if (value == null || value.isBlank()) {
                throw new IllegalStateException("Missing required test environment variable: " + name);
            }
            return value;
        }

        private static void validateUrl(String url) {
            try {
                if (!url.startsWith("jdbc:mysql://")) {
                    throw new IllegalArgumentException();
                }
                URI uri = URI.create(url.substring(5));
                if (uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null
                        || !"/ticketflow_test".equals(uri.getRawPath())) {
                    throw new IllegalArgumentException();
                }
                // Do not permit connection properties that override credentials/catalog or execute SQL.
                Set<String> allowed = Set.of("useUnicode", "characterEncoding", "serverTimezone",
                        "connectionTimeZone", "useSSL", "sslMode", "allowPublicKeyRetrieval",
                        "connectTimeout", "socketTimeout");
                if (uri.getRawQuery() != null) {
                    for (String parameter : uri.getRawQuery().split("&")) {
                        if (!allowed.contains(parameter.split("=", 2)[0])) {
                            throw new IllegalArgumentException();
                        }
                    }
                }
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("Tests require a single-host MySQL URL for ticketflow_test"
                        + " with only approved connection properties");
            }
        }
    }

    @BeforeEach
    void prepareDatabaseAndCreator() throws Exception {
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertTrue(AopUtils.isAopProxy(ticketService), "Call the Spring transactional service proxy");
        assertFalse(mockingDetails(ticketMapper).isMock(), "TicketMapper must be real");
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
        for (String table : Set.of("sys_user", "ticket", "ticket_operation_log")) {
            assertEquals("InnoDB", jdbc.queryForObject(
                    "SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE()"
                            + " AND TABLE_NAME = ?", String.class, table));
        }
        marker = "it_" + UUID.randomUUID().toString().replace("-", "");
        creator = new User();
        creator.setUsername(marker);
        creator.setPassword("integration-test-fixture");
        creator.setEmail(marker + "@example.invalid");
        creator.setStatus(1);
        creator.setCreatedAt(LocalDateTime.now());
        creator.setUpdatedAt(LocalDateTime.now());
        assertEquals(1, userMapper.insert(creator));
        assertNotNull(creator.getId());
    }

    @AfterEach
    void cleanOwnData() {
        if (creator != null && creator.getId() != null) {
            // No global deletes or truncation. Cleanup runs after all transaction assertions.
            jdbc.update("DELETE FROM ticket_operation_log WHERE operator_id = ?", creator.getId());
            jdbc.update("DELETE FROM ticket WHERE creator_id = ? AND title = ?", creator.getId(), marker);
            jdbc.update("DELETE FROM sys_user WHERE id = ? AND username = ?", creator.getId(), marker);
        }
    }

    protected TicketCreateRequest request() {
        TicketCreateRequest request = new TicketCreateRequest();
        request.setCreatorId(creator.getId());
        request.setTitle(marker);
        request.setDescription("TicketService transaction integration test " + marker);
        request.setPriority(TicketPriority.HIGH);
        return request;
    }

    protected long ticketCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM ticket WHERE title = ?", Long.class, marker);
    }

    protected long logCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM ticket_operation_log WHERE operator_id = ?",
                Long.class, creator.getId());
    }
}
