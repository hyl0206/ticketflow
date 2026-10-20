package com.ticketflow;

import com.ticketflow.auth.AuthService;
import com.ticketflow.auth.JwtTokenService;
import com.ticketflow.auth.LoginRequest;
import com.ticketflow.auth.RegisterRequest;
import com.ticketflow.auth.TokenResponse;
import com.ticketflow.entity.Role;
import com.ticketflow.entity.User;
import com.ticketflow.entity.UserRole;
import com.ticketflow.exception.BusinessException;
import com.ticketflow.mapper.RoleMapper;
import com.ticketflow.mapper.UserMapper;
import com.ticketflow.mapper.UserRoleMapper;
import com.ticketflow.security.AuthenticatedUser;
import com.ticketflow.security.UserIdentityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthServiceTest {
    private final UserMapper users = mock(UserMapper.class);
    private final RoleMapper roles = mock(RoleMapper.class);
    private final UserRoleMapper userRoles = mock(UserRoleMapper.class);
    private final UserIdentityService identities = mock(UserIdentityService.class);
    private final JwtTokenService tokens = mock(JwtTokenService.class);
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder(4);
    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(users, roles, userRoles, passwords, identities, tokens);
    }

    @Test
    void registrationHashesPasswordAndGrantsOnlyDefaultUserRole() {
        Role userRole = new Role();
        userRole.setId(3L);
        userRole.setRoleCode("USER");
        userRole.setStatus(1);
        when(roles.selectOne(any())).thenReturn(userRole);
        when(users.insert(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(42L);
            return 1;
        });
        when(userRoles.insert(any(UserRole.class))).thenReturn(1);

        AuthenticatedUser result = service.register(new RegisterRequest("alice", "Secret123!", "a@example.test"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).insert(saved.capture());
        assertNotEquals("Secret123!", saved.getValue().getPassword());
        assertTrue(passwords.matches("Secret123!", saved.getValue().getPassword()));
        assertEquals(1, saved.getValue().getStatus());
        assertEquals("a@example.test", saved.getValue().getEmail());
        ArgumentCaptor<UserRole> relation = ArgumentCaptor.forClass(UserRole.class);
        verify(userRoles).insert(relation.capture());
        assertEquals(42L, relation.getValue().getUserId());
        assertEquals(3L, relation.getValue().getRoleId());
        assertEquals(Set.of("USER"), result.roles());
        verifyNoInteractions(tokens);
    }

    @Test
    void duplicateUsernameIsConflictWithoutWrites() {
        when(users.selectOne(any())).thenReturn(user("alice", 1));
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.register(new RegisterRequest("alice", "Secret123!", null)));
        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        verify(users, never()).insert(any(User.class));
        verifyNoInteractions(userRoles, roles);
    }

    @Test
    void concurrentDuplicateRegistrationIsAlsoConflict() {
        Role role = new Role();
        role.setId(3L);
        when(roles.selectOne(any())).thenReturn(role);
        when(users.insert(any(User.class))).thenThrow(new DuplicateKeyException("duplicate username"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.register(new RegisterRequest("alice", "Secret123!", null)));
        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        verifyNoInteractions(userRoles);
    }

    @Test
    void registrationDoesNotCreateUserWhenDefaultRoleIsMissing() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.register(new RegisterRequest("alice", "Secret123!", null)));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatus());
        verify(users, never()).insert(any(User.class));
    }

    @Test
    void multibytePasswordCannotExceedBcryptByteLimit() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.register(new RegisterRequest("alice", "密".repeat(25), null)));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        verifyNoInteractions(users, roles, userRoles);
    }

    @Test
    void validLoginIssuesTokenForDatabaseIdentity() {
        User user = user("alice", 1);
        AuthenticatedUser identity = new AuthenticatedUser(42L, "alice", Set.of("USER"));
        TokenResponse expected = new TokenResponse("signed-token", "Bearer", 3600, identity);
        when(users.selectOne(any())).thenReturn(user);
        when(identities.identityFor(user)).thenReturn(identity);
        when(tokens.issue(identity)).thenReturn(expected);

        assertSame(expected, service.login(new LoginRequest("alice", "Secret123!")));
    }

    @Test
    void disabledUserCannotLoginEvenWithCorrectPassword() {
        when(users.selectOne(any())).thenReturn(user("alice", 0));
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.login(new LoginRequest("alice", "Secret123!")));
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
        verifyNoInteractions(tokens, identities);
    }

    @Test
    void WrongPasswordAndUnknownUserHaveSamePublicError() {
        when(users.selectOne(any())).thenReturn(user("alice", 1));
        BusinessException wrongPassword = assertThrows(BusinessException.class,
                () -> service.login(new LoginRequest("alice", "wrong-password")));
        when(users.selectOne(any())).thenReturn(null);
        BusinessException unknownUser = assertThrows(BusinessException.class,
                () -> service.login(new LoginRequest("missing", "wrong-password")));
        assertEquals(HttpStatus.UNAUTHORIZED, wrongPassword.getStatus());
        assertEquals(wrongPassword.getMessage(), unknownUser.getMessage());
        verifyNoInteractions(tokens);
    }

    private User user(String username, int status) {
        User user = new User();
        user.setId(42L);
        user.setUsername(username);
        user.setStatus(status);
        user.setPassword(passwords.encode("Secret123!"));
        return user;
    }
}
