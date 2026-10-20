package com.ticketflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ticketflow.dto.*;
import com.ticketflow.entity.*;
import com.ticketflow.enums.*;
import com.ticketflow.exception.*;
import com.ticketflow.mapper.*;
import com.ticketflow.security.AuthenticatedUser;
import com.ticketflow.security.CurrentUserService;
import com.ticketflow.service.TicketService;
import com.ticketflow.util.TicketNoGenerator;
import com.ticketflow.vo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class TicketServiceImpl implements TicketService {
    private final TicketMapper ticketMapper;
    private final UserMapper userMapper;
    private final TicketOperationLogMapper operationLogMapper;
    private final CurrentUserService currentUserService;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;

    @Override
    public TicketPageResponse queryTickets(TicketQueryRequest request) {
        AuthenticatedUser actor = currentUserService.require();
        requireKnownRole(actor);
        String keyword = request.getKeyword() == null ? null : request.getKeyword().strip();
        LambdaQueryWrapper<Ticket> query = new LambdaQueryWrapper<Ticket>()
                .select(Ticket::getId, Ticket::getTicketNo, Ticket::getTitle, Ticket::getStatus,
                        Ticket::getPriority, Ticket::getCreatorId, Ticket::getCreatedAt)
                .like(keyword != null && !keyword.isEmpty(), Ticket::getTitle, keyword)
                .eq(request.getStatus() != null, Ticket::getStatus, request.getStatus())
                .eq(request.getPriority() != null, Ticket::getPriority, request.getPriority())
                .eq(request.getCreatorId() != null, Ticket::getCreatorId, request.getCreatorId());
        if (!actor.hasRole("ADMIN")) {
            query.and(scope -> {
                if (actor.hasRole("USER")) {
                    scope.eq(Ticket::getCreatorId, actor.id());
                }
                if (actor.hasRole("AGENT")) {
                    if (actor.hasRole("USER")) scope.or();
                    scope.eq(Ticket::getAssigneeId, actor.id());
                }
            });
        }
        query.orderByDesc(Ticket::getCreatedAt, Ticket::getId);
        Page<Ticket> page = ticketMapper.selectPage(new Page<>(request.getPageNum(), request.getPageSize()), query);
        return new TicketPageResponse(page.getRecords().stream().map(this::toListItemResponse).toList(),
                page.getTotal(), page.getCurrent(), page.getSize(), page.getPages());
    }

    @Override
    public TicketDetailResponse getTicketById(Long id) {
        AuthenticatedUser actor = currentUserService.require();
        Ticket ticket = findTicket(id);
        requireReadable(actor, ticket);
        return toDetailResponse(ticket);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TicketCreateResponse createTicket(TicketCreateRequest request) {
        AuthenticatedUser actor = currentUserService.require();
        if (!actor.hasRole("USER") && !actor.hasRole("ADMIN")) throw forbidden();
        User user = userMapper.selectById(actor.id());
        if (user == null) throw new UserNotFoundException("创建人不存在");
        if (!Integer.valueOf(1).equals(user.getStatus())) throw new UserDisabledException("用户已被禁用");
        LocalDateTime now = LocalDateTime.now();
        Ticket ticket = new Ticket();
        ticket.setTicketNo(TicketNoGenerator.generate());
        ticket.setTitle(request.getTitle());
        ticket.setDescription(request.getDescription());
        ticket.setPriority(request.getPriority());
        ticket.setCreatorId(actor.id());
        ticket.setStatus(TicketStatus.OPEN);
        ticket.setVersion(0);
        ticket.setCreatedAt(now);
        ticket.setUpdatedAt(now);
        if (ticketMapper.insert(ticket) != 1 || ticket.getId() == null) {
            throw new IllegalStateException("工单保存失败");
        }
        writeLog(ticket, actor.id(), TicketOperationType.CREATE, null, "OPEN", "创建工单", now);
        TicketCreateResponse response = new TicketCreateResponse();
        response.setId(ticket.getId());
        response.setTicketNo(ticket.getTicketNo());
        response.setTitle(ticket.getTitle());
        response.setStatus(ticket.getStatus());
        response.setPriority(ticket.getPriority());
        response.setCreatorId(ticket.getCreatorId());
        response.setCreatedAt(ticket.getCreatedAt());
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TicketDetailResponse updateTicket(Long id, TicketUpdateRequest request) {
        AuthenticatedUser actor = currentUserService.require();
        Ticket ticket = findTicket(id);
        requireOwnerOrAdmin(actor, ticket);
        if (ticket.getStatus() != TicketStatus.OPEN) throw invalidState();
        if (!request.isChangeProvided()) throw new BusinessException("至少提供一个可修改字段");
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<Ticket> update = versionedUpdate(ticket, now);
        String oldPriority = ticket.getPriority().name();
        if (request.getTitle() != null) { update.set(Ticket::getTitle, request.getTitle()); ticket.setTitle(request.getTitle()); }
        if (request.getDescription() != null) { update.set(Ticket::getDescription, request.getDescription()); ticket.setDescription(request.getDescription()); }
        if (request.getPriority() != null) { update.set(Ticket::getPriority, request.getPriority()); ticket.setPriority(request.getPriority()); }
        persistUpdate(ticket, update, now);
        writeLog(ticket, actor.id(), TicketOperationType.UPDATE, ticket.getStatus().name(), ticket.getStatus().name(),
                "修改工单内容", now);
        if (!oldPriority.equals(ticket.getPriority().name())) {
            writeLog(ticket, actor.id(), TicketOperationType.PRIORITY_CHANGE, oldPriority, ticket.getPriority().name(),
                    "修改优先级", now);
        }
        return toDetailResponse(ticket);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TicketDetailResponse assignTicket(Long id, Long assigneeId) {
        AuthenticatedUser actor = currentUserService.require();
        if (!actor.hasRole("ADMIN")) throw forbidden();
        Ticket ticket = findTicket(id);
        if (ticket.getStatus() != TicketStatus.OPEN || ticket.getAssigneeId() != null) throw invalidState();
        User assignee = userMapper.selectById(assigneeId);
        if (assignee == null || !Integer.valueOf(1).equals(assignee.getStatus()) || !isActiveAgent(assigneeId)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_ASSIGNEE", "处理人必须是启用的客服");
        }
        return transition(ticket, actor, TicketStatus.ASSIGNED, TicketOperationType.ASSIGN, assigneeId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TicketDetailResponse startTicket(Long id) {
        AuthenticatedUser actor = currentUserService.require();
        Ticket ticket = findTicket(id);
        requireAssigneeOrAdmin(actor, ticket);
        if (ticket.getStatus() != TicketStatus.ASSIGNED) throw invalidState();
        return transition(ticket, actor, TicketStatus.PROCESSING, TicketOperationType.STATUS_CHANGE, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TicketDetailResponse resolveTicket(Long id) {
        AuthenticatedUser actor = currentUserService.require();
        Ticket ticket = findTicket(id);
        requireAssigneeOrAdmin(actor, ticket);
        if (ticket.getStatus() != TicketStatus.PROCESSING) throw invalidState();
        return transition(ticket, actor, TicketStatus.RESOLVED, TicketOperationType.RESOLVE, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TicketDetailResponse closeTicket(Long id) {
        AuthenticatedUser actor = currentUserService.require();
        Ticket ticket = findTicket(id);
        requireOwnerOrAdmin(actor, ticket);
        if (ticket.getStatus() != TicketStatus.RESOLVED) throw invalidState();
        return transition(ticket, actor, TicketStatus.CLOSED, TicketOperationType.CLOSE, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TicketDetailResponse reopenTicket(Long id) {
        AuthenticatedUser actor = currentUserService.require();
        Ticket ticket = findTicket(id);
        requireOwnerOrAdmin(actor, ticket);
        if (ticket.getStatus() != TicketStatus.RESOLVED) throw invalidState();
        return transition(ticket, actor, TicketStatus.PROCESSING, TicketOperationType.REOPEN, null);
    }

    @Override
    public List<TicketOperationResponse> getOperations(Long id) {
        AuthenticatedUser actor = currentUserService.require();
        Ticket ticket = findTicket(id);
        requireReadable(actor, ticket);
        return operationLogMapper.selectList(new LambdaQueryWrapper<TicketOperationLog>()
                        .eq(TicketOperationLog::getTicketId, id)
                        .orderByAsc(TicketOperationLog::getCreatedAt, TicketOperationLog::getId))
                .stream().map(log -> new TicketOperationResponse(log.getId(), log.getOperatorId(),
                        log.getOperationType(), log.getOldValue(), log.getNewValue(), log.getRemark(), log.getCreatedAt())).toList();
    }

    private TicketDetailResponse transition(Ticket ticket, AuthenticatedUser actor, TicketStatus target,
                                            TicketOperationType operation, Long assigneeId) {
        TicketStatus previous = ticket.getStatus();
        if (previous == null || !previous.canTransitionTo(target)) throw invalidState();
        if (target != TicketStatus.ASSIGNED && ticket.getAssigneeId() == null) {
            throw new BusinessException(HttpStatus.CONFLICT, "TICKET_ASSIGNEE_MISSING", "工单尚未分配处理人");
        }
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<Ticket> update = versionedUpdate(ticket, now).set(Ticket::getStatus, target);
        if (assigneeId != null) {
            update.isNull(Ticket::getAssigneeId).set(Ticket::getAssigneeId, assigneeId);
            ticket.setAssigneeId(assigneeId);
        }
        if (target == TicketStatus.RESOLVED) {
            update.set(Ticket::getResolvedAt, now);
            ticket.setResolvedAt(now);
        }
        if (target == TicketStatus.CLOSED) {
            update.set(Ticket::getClosedAt, now);
            ticket.setClosedAt(now);
        }
        if (operation == TicketOperationType.REOPEN) {
            update.set(Ticket::getResolvedAt, null).set(Ticket::getClosedAt, null);
            ticket.setResolvedAt(null);
            ticket.setClosedAt(null);
        }
        persistUpdate(ticket, update, now);
        ticket.setStatus(target);
        writeLog(ticket, actor.id(), operation, previous.name(), target.name(),
                assigneeId == null ? "状态变更" : "分配客服：" + assigneeId, now);
        return toDetailResponse(ticket);
    }

    private LambdaUpdateWrapper<Ticket> versionedUpdate(Ticket ticket, LocalDateTime now) {
        if (ticket.getVersion() == null || ticket.getStatus() == null) throw invalidState();
        // Explicit compare-and-set also handles null timestamp clearing on reopen.
        return new LambdaUpdateWrapper<Ticket>().eq(Ticket::getId, ticket.getId())
                .eq(Ticket::getVersion, ticket.getVersion()).eq(Ticket::getStatus, ticket.getStatus())
                .set(Ticket::getVersion, ticket.getVersion() + 1).set(Ticket::getUpdatedAt, now);
    }

    private void persistUpdate(Ticket ticket, LambdaUpdateWrapper<Ticket> update, LocalDateTime now) {
        if (ticketMapper.update(null, update) != 1) {
            throw new BusinessException(HttpStatus.CONFLICT, "TICKET_CONFLICT", "工单已被其他请求修改，请刷新后重试");
        }
        ticket.setVersion(ticket.getVersion() + 1);
        ticket.setUpdatedAt(now);
    }

    private void writeLog(Ticket ticket, Long operatorId, TicketOperationType type, String oldValue,
                          String newValue, String remark, LocalDateTime now) {
        TicketOperationLog log = new TicketOperationLog();
        log.setTicketId(ticket.getId());
        log.setOperatorId(operatorId);
        log.setOperationType(type);
        log.setOldValue(oldValue);
        log.setNewValue(newValue);
        log.setRemark(remark);
        log.setCreatedAt(now);
        if (operationLogMapper.insert(log) != 1) throw new IllegalStateException("操作日志保存失败");
    }

    private Ticket findTicket(Long id) {
        if (id == null || id <= 0) throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "工单ID必须大于0");
        Ticket ticket = ticketMapper.selectById(id);
        if (ticket == null) throw new BusinessException(HttpStatus.NOT_FOUND, "TICKET_NOT_FOUND", "工单不存在");
        return ticket;
    }

    private boolean isActiveAgent(Long userId) {
        Role role = roleMapper.selectOne(new LambdaQueryWrapper<Role>().eq(Role::getRoleCode, "AGENT").eq(Role::getStatus, 1));
        return role != null && userRoleMapper.selectCount(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getUserId, userId).eq(UserRole::getRoleId, role.getId())) > 0;
    }

    private void requireKnownRole(AuthenticatedUser actor) {
        if (!actor.hasRole("ADMIN") && !actor.hasRole("USER") && !actor.hasRole("AGENT")) throw forbidden();
    }

    private void requireReadable(AuthenticatedUser actor, Ticket ticket) {
        if (actor.hasRole("ADMIN")) return;
        if (actor.hasRole("USER") && Objects.equals(actor.id(), ticket.getCreatorId())) return;
        if (actor.hasRole("AGENT") && Objects.equals(actor.id(), ticket.getAssigneeId())) return;
        throw forbidden();
    }

    private void requireOwnerOrAdmin(AuthenticatedUser actor, Ticket ticket) {
        if (!actor.hasRole("ADMIN") && !(actor.hasRole("USER") && Objects.equals(actor.id(), ticket.getCreatorId()))) throw forbidden();
    }

    private void requireAssigneeOrAdmin(AuthenticatedUser actor, Ticket ticket) {
        if (!actor.hasRole("ADMIN") && !(actor.hasRole("AGENT") && Objects.equals(actor.id(), ticket.getAssigneeId()))) throw forbidden();
    }

    private BusinessException forbidden() {
        return new BusinessException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "无权执行此操作");
    }

    private BusinessException invalidState() {
        return new BusinessException(HttpStatus.CONFLICT, "INVALID_TICKET_STATE", "当前工单状态不允许此操作");
    }

    private TicketListItemResponse toListItemResponse(Ticket ticket) {
        TicketListItemResponse response = new TicketListItemResponse();
        response.setId(ticket.getId()); response.setTicketNo(ticket.getTicketNo()); response.setTitle(ticket.getTitle());
        response.setStatus(ticket.getStatus()); response.setPriority(ticket.getPriority());
        response.setCreatorId(ticket.getCreatorId()); response.setCreatedAt(ticket.getCreatedAt());
        return response;
    }

    private TicketDetailResponse toDetailResponse(Ticket ticket) {
        TicketDetailResponse response = new TicketDetailResponse();
        response.setId(ticket.getId()); response.setTicketNo(ticket.getTicketNo()); response.setTitle(ticket.getTitle());
        response.setDescription(ticket.getDescription()); response.setStatus(ticket.getStatus());
        response.setPriority(ticket.getPriority()); response.setCreatorId(ticket.getCreatorId());
        response.setAssigneeId(ticket.getAssigneeId()); response.setDueDate(ticket.getDueDate());
        response.setCreatedAt(ticket.getCreatedAt()); response.setUpdatedAt(ticket.getUpdatedAt());
        response.setResolvedAt(ticket.getResolvedAt()); response.setClosedAt(ticket.getClosedAt());
        return response;
    }
}
