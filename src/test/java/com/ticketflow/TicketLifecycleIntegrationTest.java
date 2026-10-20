package com.ticketflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketflow.entity.Ticket;
import com.ticketflow.mapper.TicketMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.transaction.BeforeTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real bearer tokens, Spring Security, service transactions and isolated MySQL persistence. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = TicketServiceTestSupport.TestDatabaseInitializer.class)
@Transactional
class TicketLifecycleIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired TicketMapper tickets;

    private Account owner;
    private Account stranger;
    private Account agent;
    private Account admin;

    record Account(long id, String token) { }

    @BeforeTransaction
    void guardDatabaseAndPrepareSchema() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertEquals("ticketflow_test", connection.getCatalog());
            try (var statement = connection.createStatement();
                 var result = statement.executeQuery("SELECT DATABASE(), CURRENT_USER()")) {
                assertTrue(result.next());
                assertEquals("ticketflow_test", result.getString(1));
                assertTrue(result.getString(2).startsWith("ticketflow_test_user@"));
            }
            var script = new ResourceDatabasePopulator(new ClassPathResource("db/schema-test.sql"));
            script.setSqlScriptEncoding("UTF-8");
            script.populate(connection);
        }
    }

    @BeforeEach
    void createAccounts() throws Exception {
        owner = account("USER");
        stranger = account("USER");
        agent = account("AGENT");
        admin = account("ADMIN");
    }

    @Test
    void completeLifecyclePersistsStatesTimesAndAuthenticatedAudit() throws Exception {
        long id = create(owner);
        assertEquals(owner.id(), tickets.selectById(id).getCreatorId());
        assertNull(tickets.selectById(id).getAssigneeId());

        assertEquals("修订标题", data(patch("/api/tickets/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"修订标题\",\"priority\":\"URGENT\"}"), owner).path("title").asText());
        assertEquals("修订标题", tickets.selectById(id).getTitle());
        assign(id, agent, admin);
        state(post("/api/tickets/{id}/start", id), agent, "PROCESSING");
        state(post("/api/tickets/{id}/resolve", id), agent, "RESOLVED");
        assertNotNull(tickets.selectById(id).getResolvedAt());
        state(post("/api/tickets/{id}/reopen", id), owner, "PROCESSING");
        assertNull(tickets.selectById(id).getResolvedAt(), "Reopening must clear persisted resolved time");
        assertNull(tickets.selectById(id).getClosedAt());
        state(post("/api/tickets/{id}/resolve", id), agent, "RESOLVED");
        state(post("/api/tickets/{id}/close", id), owner, "CLOSED");
        Ticket saved = tickets.selectById(id);
        assertNotNull(saved.getResolvedAt());
        assertNotNull(saved.getClosedAt());
        assertEquals(agent.id(), saved.getAssigneeId());
        assertEquals(7, saved.getVersion());

        JsonNode operations = data(get("/api/tickets/{id}/operations", id), owner);
        assertEquals(9, operations.size()); // CREATE, UPDATE, PRIORITY_CHANGE, six lifecycle changes.
        assertEquals("CREATE", operations.get(0).path("operationType").asText());
        assertEquals(owner.id(), operations.get(0).path("operatorId").asLong());
        JsonNode assignment = operations.get(3);
        assertEquals(admin.id(), assignment.path("operatorId").asLong());
        assertEquals("OPEN", assignment.path("oldValue").asText());
        assertEquals("ASSIGNED", assignment.path("newValue").asText());
        assertEquals("CLOSED", operations.get(8).path("newValue").asText());

        long logsBefore = logs(id);
        mvc.perform(post("/api/tickets/{id}/reopen", id).header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("INVALID_TICKET_STATE"));
        assertEquals(logsBefore, logs(id));
        assertEquals(7, tickets.selectById(id).getVersion());
    }

    @Test
    void dataPermissionsApplyToListDetailAndAuditWithoutFilterBypass() throws Exception {
        long ownId = create(owner);
        long otherId = create(stranger);
        assertEquals(1, data(get("/api/tickets"), owner).path("total").asLong());
        assertEquals(0, data(get("/api/tickets").param("creatorId", Long.toString(stranger.id())), owner)
                .path("total").asLong());
        assertEquals(0, data(get("/api/tickets"), agent).path("total").asLong());
        assign(ownId, agent, admin);
        assertEquals(1, data(get("/api/tickets"), agent).path("total").asLong());
        assertEquals(ownId, data(get("/api/tickets"), agent).path("records").get(0).path("id").asLong());
        assertTrue(data(get("/api/tickets"), admin).path("total").asLong() >= 2);
        for (String suffix : new String[]{"", "/operations"}) {
            mvc.perform(get("/api/tickets/" + otherId + suffix).header("Authorization", "Bearer " + owner.token()))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
        }
    }

    @Test
    void combinedUserAgentRolesUseUnionInsideOtherQueryFilters() throws Exception {
        long assigned = create(owner);
        long own = create(agent);
        create(stranger);
        assign(assigned, agent, admin);
        JsonNode page = data(get("/api/tickets"), agent);
        assertEquals(2, page.path("total").asLong());
        assertEquals(1, data(get("/api/tickets").param("status", "OPEN"), agent).path("total").asLong());
        assertEquals(own, data(get("/api/tickets").param("status", "OPEN"), agent)
                .path("records").get(0).path("id").asLong());
    }

    @Test
    void unauthorizedAndInvalidTransitionsNeverChangeTicketOrAudit() throws Exception {
        long id = create(owner);
        long before = logs(id);
        mvc.perform(post("/api/tickets/{id}/assign", id).header("Authorization", "Bearer " + owner.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"assigneeId\":" + agent.id() + "}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/tickets/{id}/close", id).header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isConflict());
        assertEquals(before, logs(id));
        assertEquals(0, tickets.selectById(id).getVersion());
        assign(id, agent, admin);
        mvc.perform(post("/api/tickets/{id}/start", id).header("Authorization", "Bearer " + stranger.token()))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/tickets/{id}", id).header("Authorization", "Bearer " + owner.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"changed\"}"))
                .andExpect(status().isConflict());
        assertEquals(1, tickets.selectById(id).getVersion());
        assertEquals(before + 1, logs(id));
    }

    @Test
    void disabledOrNonAgentAssigneeIsRejectedWithoutWrites() throws Exception {
        long id = create(owner);
        mvc.perform(post("/api/tickets/{id}/assign", id).header("Authorization", "Bearer " + admin.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"assigneeId\":" + stranger.id() + "}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("INVALID_ASSIGNEE"));
        jdbc.update("UPDATE sys_user SET status = 0 WHERE id = ?", agent.id());
        mvc.perform(post("/api/tickets/{id}/assign", id).header("Authorization", "Bearer " + admin.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"assigneeId\":" + agent.id() + "}"))
                .andExpect(status().isBadRequest());
        assertEquals(0, tickets.selectById(id).getVersion());
        assertEquals(1, logs(id));
    }

    @Test
    void clientsCannotSpoofIdentityStateOrPersistenceFields() throws Exception {
        mvc.perform(post("/api/tickets").header("Authorization", "Bearer " + owner.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"description\":\"d\",\"priority\":\"LOW\",\"creatorId\":" + stranger.id() + "}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST_BODY"));
        long id = create(owner);
        for (String body : new String[]{"{}", "{\"status\":\"CLOSED\"}", "{\"version\":10}",
                "{\"title\":\"   \"}", "{\"priority\":\"UNKNOWN\"}", "{\"priority\":1}"}) {
            mvc.perform(patch("/api/tickets/{id}", id).header("Authorization", "Bearer " + owner.token())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/tickets/not-a-number").header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("INVALID_PARAMETER"));
        assertEquals(0, tickets.selectById(id).getVersion());
        assertEquals(1, logs(id));
    }

    @Test
    void numericEnumAndFractionalAssigneeAreRejectedWithoutWrites() throws Exception {
        mvc.perform(post("/api/tickets").header("Authorization", "Bearer " + owner.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"description\":\"d\",\"priority\":1}"))
                .andExpect(status().isBadRequest());
        long id = create(owner);
        mvc.perform(post("/api/tickets/{id}/assign", id).header("Authorization", "Bearer " + admin.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"assigneeId\":1.9}"))
                .andExpect(status().isBadRequest());
        assertEquals(0, tickets.selectById(id).getVersion());
        assertEquals(1, logs(id));
    }

    @Test
    void openApiDocumentsBearerAndPublicLoginAndMutationRoutes() throws Exception {
        JsonNode spec = json.readTree(mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertEquals("bearer", spec.path("components").path("securitySchemes").path("bearerAuth").path("scheme").asText());
        assertTrue(spec.path("paths").has("/api/tickets/{id}/assign"));
        assertTrue(spec.path("paths").has("/api/auth/login"));
        assertTrue(spec.path("paths").path("/api/auth/login").path("post").path("security").isEmpty());
    }

    private Account account(String role) throws Exception {
        String name = "d7_" + UUID.randomUUID().toString().replace("-", "");
        String body = "{\"username\":\"" + name + "\",\"password\":\"TestPass123!\"}";
        JsonNode registration = json.readTree(mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsByteArray()).path("data");
        long id = registration.path("id").asLong();
        if (!role.equals("USER")) {
            jdbc.update("INSERT INTO sys_user_role(user_id,role_id) SELECT ?,id FROM sys_role WHERE role_code = ?", id, role);
        }
        JsonNode login = json.readTree(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray()).path("data");
        return new Account(id, login.path("accessToken").asText());
    }

    private long create(Account account) throws Exception {
        return json.readTree(mvc.perform(post("/api/tickets").header("Authorization", "Bearer " + account.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"测试工单\",\"description\":\"测试描述\",\"priority\":\"HIGH\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsByteArray())
                .path("data").path("id").asLong();
    }

    private JsonNode data(MockHttpServletRequestBuilder request, Account account) throws Exception {
        return json.readTree(mvc.perform(request.header("Authorization", "Bearer " + account.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsByteArray()).path("data");
    }

    private void assign(long id, Account assignee, Account actor) throws Exception {
        state(post("/api/tickets/{id}/assign", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"assigneeId\":" + assignee.id() + "}"), actor, "ASSIGNED");
    }

    private void state(MockHttpServletRequestBuilder request, Account account, String expected) throws Exception {
        assertEquals(expected, data(request, account).path("status").asText());
    }

    private long logs(long id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM ticket_operation_log WHERE ticket_id = ?", Long.class, id);
    }
}
