package com.ticketflow;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketflow.entity.Ticket;
import com.ticketflow.entity.User;
import com.ticketflow.enums.TicketPriority;
import com.ticketflow.enums.TicketStatus;
import com.ticketflow.exception.BusinessException;
import com.ticketflow.mapper.TicketMapper;
import com.ticketflow.mapper.UserMapper;
import com.ticketflow.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.transaction.BeforeTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = TicketServiceTestSupport.TestDatabaseInitializer.class)
@Transactional
class TicketQueryIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TicketMapper ticketMapper;
    @Autowired private UserMapper userMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private DataSource dataSource;
    @Autowired private ApplicationContext context;

    private List<Ticket> fixtures;
    private List<Ticket> sorted;
    private AuthenticatedUser queryAdmin;

    @BeforeTransaction
    void verifyDatabaseAndPrepareSchema() throws Exception {
        // Reuse the existing test schema only after checking the actual connection identity.
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
        assertEquals("InnoDB", jdbc.queryForObject(
                "SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE()"
                        + " AND TABLE_NAME = 'ticket'", String.class));
    }

    @BeforeEach
    void prepareFixtures() {
        assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
        // This DELETE and all fixture INSERTs roll back together after each test.
        jdbc.update("DELETE FROM ticket");
        User admin = new User();
        String marker = "query_" + UUID.randomUUID().toString().replace("-", "");
        admin.setUsername(marker);
        admin.setPassword("query-regression-fixture");
        admin.setEmail(marker + "@example.invalid");
        admin.setStatus(1);
        admin.setCreatedAt(LocalDateTime.now());
        admin.setUpdatedAt(admin.getCreatedAt());
        assertEquals(1, userMapper.insert(admin));
        // This regression suite tests query behavior; JWT/RBAC are exercised in dedicated tests.
        queryAdmin = new AuthenticatedUser(admin.getId(), admin.getUsername(), Set.of("ADMIN"));
        fixtures = new ArrayList<>();
        for (int i = 0; i < 14; i++) {
            Ticket ticket = new Ticket();
            ticket.setTicketNo("Q" + UUID.randomUUID().toString().replace("-", "").substring(0, 30));
            ticket.setTitle((i % 2 == 0 ? "网络故障 " : "登录异常 ") + i);
            ticket.setDescription("网络故障仅出现在部分工单的描述中");
            ticket.setStatus(TicketStatus.values()[i % TicketStatus.values().length]);
            ticket.setPriority(TicketPriority.values()[i % TicketPriority.values().length]);
            ticket.setCreatorId(i % 3 == 0 ? 101L : 202L);
            // Newer IDs have older times; adjacent pairs share a time to exercise both sort keys.
            ticket.setCreatedAt(LocalDateTime.of(2026, 1, 1, 12, 0).plusDays(6 - i / 2));
            ticket.setUpdatedAt(ticket.getCreatedAt());
            ticket.setVersion(0);
            assertEquals(1, ticketMapper.insert(ticket));
            fixtures.add(ticket);
        }
        sorted = fixtures.stream().sorted(Comparator.comparing(Ticket::getCreatedAt).reversed()
                .thenComparing(Comparator.comparing(Ticket::getId).reversed())).toList();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void defaultsReturnFirstTenInStableOrderAndOnlyListFields() throws Exception {
        assertPage(query(get("/api/tickets")), sorted, 1, 10);
    }

    @Test
    void specifiedPageReturnsCorrectSliceAndMetadata() throws Exception {
        assertPage(query(get("/api/tickets").param("pageNum", "2").param("pageSize", "3")),
                sorted, 2, 3);
    }

    @ParameterizedTest
    @ValueSource(strings = {"网络", "  网络  ", "\u3000网络\u3000"})
    void keywordMatchesOnlyTitleAndTrimsWhitespace(String keyword) throws Exception {
        List<Ticket> expected = sorted.stream().filter(t -> t.getTitle().contains("网络")).toList();
        assertEquals(7, expected.size());
        assertPage(query(get("/api/tickets").param("keyword", keyword)), expected, 1, 10);
    }

    @Test
    void keywordDoesNotMatchTicketNumber() throws Exception {
        assertPage(query(get("/api/tickets").param("keyword", fixtures.getFirst().getTicketNo())),
                List.of(), 1, 10);
    }

    @ParameterizedTest
    @EnumSource(TicketStatus.class)
    void statusMatchesExactly(TicketStatus value) throws Exception {
        assertPage(query(get("/api/tickets").param("status", value.name())),
                sorted.stream().filter(t -> t.getStatus() == value).toList(), 1, 10);
    }

    @ParameterizedTest
    @EnumSource(TicketPriority.class)
    void priorityMatchesExactly(TicketPriority value) throws Exception {
        assertPage(query(get("/api/tickets").param("priority", value.name())),
                sorted.stream().filter(t -> t.getPriority() == value).toList(), 1, 10);
    }

    @ParameterizedTest
    @ValueSource(longs = {101, 202, 999})
    void administratorCanFilterByAnyCreatorId(long creatorId) throws Exception {
        assertPage(query(get("/api/tickets").param("creatorId", Long.toString(creatorId))),
                sorted.stream().filter(t -> t.getCreatorId() == creatorId).toList(), 1, 10);
    }

    @Test
    void allFiltersCombineWithAnd() throws Exception {
        Ticket target = fixtures.getFirst();
        insertCombinationNearMatch("登录异常", target.getPriority(), target.getCreatorId());
        insertCombinationNearMatch(target.getTitle(), TicketPriority.HIGH, target.getCreatorId());
        insertCombinationNearMatch(target.getTitle(), target.getPriority(), 202L);

        assertPage(query(get("/api/tickets").param("keyword", "  网络  ").param("status", "OPEN")
                        .param("priority", "LOW").param("creatorId", "101")),
                List.of(fixtures.getFirst()), 1, 10);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", " \t ", "\u3000"})
    void blankKeywordIsIgnored(String keyword) throws Exception {
        assertPage(query(get("/api/tickets").param("keyword", keyword)), sorted, 1, 10);
    }

    @ParameterizedTest
    @ValueSource(strings = {"不存在的标题", "' OR 1=1 --"})
    void noMatchesReturnEmptyPage(String keyword) throws Exception {
        assertPage(query(get("/api/tickets").param("keyword", keyword)), List.of(), 1, 10);
    }

    @Test
    void emptyTableReturnsEmptyPage() throws Exception {
        jdbc.update("DELETE FROM ticket");
        assertPage(query(get("/api/tickets")), List.of(), 1, 10);
    }

    @Test
    void beyondLastPageKeepsFilteredTotal() throws Exception {
        assertPage(query(get("/api/tickets").param("pageNum", "4").param("pageSize", "2")
                        .param("status", "OPEN")),
                sorted.stream().filter(t -> t.getStatus() == TicketStatus.OPEN).toList(), 4, 2);
    }

    @Test
    void largestIntegerPageAndMaximumPageSizeRemainValid() throws Exception {
        assertPage(query(get("/api/tickets").param("pageNum", "2147483647").param("pageSize", "100")),
                sorted, Integer.MAX_VALUE, 100);
    }

    @Test
    void maximumPageSizeReturnsAllRecords() throws Exception {
        assertPage(query(get("/api/tickets").param("pageSize", "100")), sorted, 1, 100);
    }

    @Test
    void paginationIsRegisteredOnceAfterOptimisticLocking() {
        var interceptors = context.getBeansOfType(MybatisPlusInterceptor.class);
        assertEquals(1, interceptors.size());
        var inner = interceptors.values().iterator().next().getInterceptors();
        assertEquals(1, inner.stream().filter(PaginationInnerInterceptor.class::isInstance).count());
        assertEquals(1, inner.stream().filter(OptimisticLockerInnerInterceptor.class::isInstance).count());
        var pagination = assertInstanceOf(PaginationInnerInterceptor.class, inner.getLast());
        assertFalse(pagination.isOverflow());
    }

    @Test
    void existingTicketReturnsCompleteDetail() throws Exception {
        Ticket ticket = fixtures.getFirst();
        ticket.setStatus(TicketStatus.CLOSED);
        ticket.setAssigneeId(303L);
        ticket.setDueDate(ticket.getCreatedAt().plusDays(1));
        ticket.setUpdatedAt(ticket.getCreatedAt().plusHours(3));
        ticket.setResolvedAt(ticket.getCreatedAt().plusHours(1));
        ticket.setClosedAt(ticket.getCreatedAt().plusHours(2));
        assertEquals(1, ticketMapper.updateById(ticket));

        JsonNode detail = query(get("/api/tickets/{id}", ticket.getId()));

        assertEquals(13, detail.size());
        assertEquals(ticket.getId(), detail.get("id").asLong());
        assertEquals(ticket.getTicketNo(), detail.get("ticketNo").asText());
        assertEquals(ticket.getTitle(), detail.get("title").asText());
        assertEquals(ticket.getDescription(), detail.get("description").asText());
        assertEquals(ticket.getStatus().name(), detail.get("status").asText());
        assertEquals(ticket.getPriority().name(), detail.get("priority").asText());
        assertEquals(ticket.getCreatorId(), detail.get("creatorId").asLong());
        assertEquals(ticket.getAssigneeId(), detail.get("assigneeId").asLong());
        assertEquals(DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(ticket.getDueDate()),
                detail.get("dueDate").asText());
        assertEquals(DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(ticket.getCreatedAt()),
                detail.get("createdAt").asText());
        assertEquals(DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(ticket.getUpdatedAt()),
                detail.get("updatedAt").asText());
        assertEquals(DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(ticket.getResolvedAt()),
                detail.get("resolvedAt").asText());
        assertEquals(DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(ticket.getClosedAt()),
                detail.get("closedAt").asText());
        assertFalse(detail.has("version"));
    }

    @Test
    void missingTicketReturnsUnifiedNotFoundResponse() throws Exception {
        Long missingId = fixtures.getFirst().getId();
        assertEquals(1, ticketMapper.deleteById(missingId));
        assertNull(ticketMapper.selectById(missingId));

        mockMvc.perform(asAdmin(get("/api/tickets/{id}", missingId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("工单不存在"))
                .andExpect(result -> {
                    BusinessException exception = assertInstanceOf(BusinessException.class,
                            result.getResolvedException());
                    assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
                });
    }

    private void insertCombinationNearMatch(String title, TicketPriority priority, Long creatorId) {
        Ticket target = fixtures.getFirst();
        Ticket ticket = new Ticket();
        ticket.setTicketNo("Q" + UUID.randomUUID().toString().replace("-", "").substring(0, 30));
        ticket.setTitle(title);
        ticket.setDescription(target.getDescription());
        ticket.setStatus(target.getStatus());
        ticket.setPriority(priority);
        ticket.setCreatorId(creatorId);
        ticket.setCreatedAt(target.getCreatedAt());
        ticket.setUpdatedAt(target.getUpdatedAt());
        ticket.setVersion(target.getVersion());
        assertEquals(1, ticketMapper.insert(ticket));
        assertNotNull(ticket.getId());
    }

    private JsonNode query(MockHttpServletRequestBuilder request) throws Exception {
        JsonNode envelope = objectMapper.readTree(mockMvc.perform(asAdmin(request)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());
        assertEquals(3, envelope.size());
        assertEquals(0, envelope.get("code").asInt());
        assertEquals("成功", envelope.get("message").asText());
        return envelope.get("data");
    }

    private MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder request) {
        return request.with(authentication(new UsernamePasswordAuthenticationToken(queryAdmin, null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))));
    }

    private void assertPage(JsonNode page, List<Ticket> expected, int pageNum, int pageSize) {
        assertEquals(expected.size(), page.get("total").asLong());
        assertEquals(pageNum, page.get("pageNum").asLong());
        assertEquals(pageSize, page.get("pageSize").asLong());
        assertEquals((expected.size() + pageSize - 1) / pageSize, page.get("pages").asLong());
        List<Ticket> slice = expected.stream().skip(((long) pageNum - 1) * pageSize)
                .limit(pageSize).toList();
        JsonNode records = page.get("records");
        assertTrue(records.isArray());
        assertEquals(slice.size(), records.size());
        for (int i = 0; i < slice.size(); i++) {
            Ticket ticket = slice.get(i);
            JsonNode record = records.get(i);
            assertEquals(7, record.size());
            assertEquals(ticket.getId(), record.get("id").asLong());
            assertEquals(ticket.getTicketNo(), record.get("ticketNo").asText());
            assertEquals(ticket.getTitle(), record.get("title").asText());
            assertEquals(ticket.getStatus().name(), record.get("status").asText());
            assertEquals(ticket.getPriority().name(), record.get("priority").asText());
            assertEquals(ticket.getCreatorId(), record.get("creatorId").asLong());
            assertEquals(DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(ticket.getCreatedAt()),
                    record.get("createdAt").asText());
        }
    }
}
