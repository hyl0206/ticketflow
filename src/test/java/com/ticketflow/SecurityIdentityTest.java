package com.ticketflow;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ticketflow.entity.Role;
import com.ticketflow.entity.User;
import com.ticketflow.entity.UserRole;
import com.ticketflow.exception.BusinessException;
import com.ticketflow.mapper.RoleMapper;
import com.ticketflow.mapper.UserMapper;
import com.ticketflow.mapper.UserRoleMapper;
import com.ticketflow.security.AuthenticatedUser;
import com.ticketflow.security.CurrentUserService;
import com.ticketflow.security.UserIdentityService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SecurityIdentityTest {
    @BeforeAll
    static void initializeMapperMetadata() {
        // Mockito mappers do not perform the metadata registration done by real MyBatis startup.
        MybatisConfiguration configuration = new MybatisConfiguration();
        for (Class<?> entity : List.of(User.class, UserRole.class, Role.class)) {
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "SecurityIdentityTest");
            assistant.setCurrentNamespace(entity.getName() + "Mapper");
            TableInfoHelper.initTableInfo(assistant, entity);
        }
    }

    @AfterEach
    void cleanupContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void currentUserRejectsMissingAndAnonymousAuthentication() {
        CurrentUserService currentUser = new CurrentUserService();
        assertEquals(HttpStatus.UNAUTHORIZED,
                assertThrows(BusinessException.class, currentUser::require).getStatus());
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "test-key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        assertThrows(BusinessException.class, currentUser::require);
    }

    @Test
    void currentUserReturnsTrustedPrincipalAndRolesAreImmutable() {
        AuthenticatedUser identity = new AuthenticatedUser(1L, "alice", Set.of("USER"));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(identity, null, List.of()));
        assertSame(identity, new CurrentUserService().require());
        assertTrue(identity.hasRole("USER"));
        assertFalse(identity.hasRole("ADMIN"));
        assertThrows(UnsupportedOperationException.class, () -> identity.roles().add("ADMIN"));
    }

    @Test
    void missingOrDisabledAccountHasNoIdentity() {
        UserMapper users = mock(UserMapper.class);
        RoleMapper roles = mock(RoleMapper.class);
        UserRoleMapper userRoles = mock(UserRoleMapper.class);
        UserIdentityService identities = new UserIdentityService(users, userRoles, roles);
        assertTrue(identities.findActive(1L).isEmpty());
        User disabled = user(0);
        when(users.selectById(1L)).thenReturn(disabled);
        assertTrue(identities.findActive(1L).isEmpty());
        verifyNoInteractions(userRoles, roles);
    }

    @Test
    void identityExcludesDisabledAndUnsupportedRoles() {
        UserMapper users = mock(UserMapper.class);
        RoleMapper roles = mock(RoleMapper.class);
        UserRoleMapper userRoles = mock(UserRoleMapper.class);
        when(users.selectById(1L)).thenReturn(user(1));
        UserRole relation = new UserRole();
        relation.setRoleId(5L);
        when(userRoles.selectList(any())).thenReturn(List.of(relation));
        when(roles.selectList(any())).thenReturn(List.of(role("USER", 1), role("ADMIN", 0), role("UNKNOWN", 1)));

        AuthenticatedUser identity = new UserIdentityService(users, userRoles, roles).findActive(1L).orElseThrow();
        assertEquals(Set.of("USER"), identity.roles());
    }

    private User user(int status) {
        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        user.setStatus(status);
        return user;
    }

    private Role role(String code, int status) {
        Role role = new Role();
        role.setRoleCode(code);
        role.setStatus(status);
        return role;
    }
}
