package com.ticketflow.security;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ticketflow.entity.Role;
import com.ticketflow.entity.User;
import com.ticketflow.entity.UserRole;
import com.ticketflow.mapper.RoleMapper;
import com.ticketflow.mapper.UserMapper;
import com.ticketflow.mapper.UserRoleMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UserIdentityService {
    private static final Set<String> SUPPORTED_ROLES = Set.of("USER", "AGENT", "ADMIN");

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;

    public UserIdentityService(UserMapper userMapper, UserRoleMapper userRoleMapper, RoleMapper roleMapper) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
    }

    /** Rechecked for every bearer request so disabling users or roles takes effect immediately. */
    public Optional<AuthenticatedUser> findActive(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            return Optional.empty();
        }
        return Optional.of(identityFor(user));
    }

    public AuthenticatedUser identityFor(User user) {
        List<Long> roleIds = userRoleMapper.selectList(Wrappers.<UserRole>lambdaQuery()
                        .eq(UserRole::getUserId, user.getId()))
                .stream().map(UserRole::getRoleId).distinct().toList();
        Set<String> roles = roleIds.isEmpty() ? Set.of() : roleMapper.selectList(
                        Wrappers.<Role>lambdaQuery().in(Role::getId, roleIds).eq(Role::getStatus, 1))
                .stream()
                .filter(role -> Integer.valueOf(1).equals(role.getStatus()))
                .map(Role::getRoleCode)
                .filter(SUPPORTED_ROLES::contains)
                .collect(Collectors.toUnmodifiableSet());
        return new AuthenticatedUser(user.getId(), user.getUsername(), roles);
    }
}
