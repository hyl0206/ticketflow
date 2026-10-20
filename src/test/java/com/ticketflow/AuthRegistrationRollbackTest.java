package com.ticketflow;

import com.ticketflow.auth.AuthService;
import com.ticketflow.auth.RegisterRequest;
import com.ticketflow.entity.UserRole;
import com.ticketflow.mapper.UserRoleMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Real user INSERT and transaction, with only role-binding failure injected. */
class AuthRegistrationRollbackTest extends TicketServiceTestSupport {
    @Autowired private AuthService authService;
    @MockitoBean private UserRoleMapper userRoleMapper;
    private String registrationUsername;

    @Test
    void roleBindingFailureRollsBackAlreadyInsertedUser() {
        registrationUsername = marker + "_new";
        AtomicReference<Long> insertedId = new AtomicReference<>();
        RuntimeException failure = new RuntimeException("Simulated role binding failure");
        doAnswer(invocation -> {
            UserRole relation = invocation.getArgument(0);
            insertedId.set(relation.getUserId());
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals(1L, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM sys_user WHERE id = ? AND username = ?",
                    Long.class, relation.getUserId(), registrationUsername));
            throw failure;
        }).when(userRoleMapper).insert(any(UserRole.class));

        assertSame(failure, assertThrows(RuntimeException.class, () -> authService.register(
                new RegisterRequest(registrationUsername, "Rollback123!", null))));
        assertNotNull(insertedId.get());
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE id = ?", Long.class, insertedId.get()));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_role WHERE user_id = ?", Long.class, insertedId.get()));
    }

    @AfterEach
    void cleanOnlyOwnRegistrationIfAnAssertionFailed() {
        if (registrationUsername != null) {
            jdbc.update("DELETE FROM sys_user_role WHERE user_id IN (SELECT id FROM sys_user WHERE username = ?)",
                    registrationUsername);
            jdbc.update("DELETE FROM sys_user WHERE username = ?", registrationUsername);
        }
    }
}
