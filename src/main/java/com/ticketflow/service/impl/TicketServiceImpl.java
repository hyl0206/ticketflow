
package com.ticketflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ticketflow.dto.TicketCreateRequest;
import com.ticketflow.dto.TicketQueryRequest;
import com.ticketflow.entity.Ticket;
import com.ticketflow.entity.TicketOperationLog;
import com.ticketflow.entity.User;
import com.ticketflow.enums.TicketOperationType;
import com.ticketflow.enums.TicketStatus;
import com.ticketflow.exception.UserDisabledException;
import com.ticketflow.exception.UserNotFoundException;
import com.ticketflow.mapper.TicketMapper;
import com.ticketflow.mapper.TicketOperationLogMapper;
import com.ticketflow.mapper.UserMapper;
import com.ticketflow.service.TicketService;
import com.ticketflow.util.TicketNoGenerator;
import com.ticketflow.vo.TicketCreateResponse;
import com.ticketflow.vo.TicketListItemResponse;
import com.ticketflow.vo.TicketPageResponse;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class TicketServiceImpl implements TicketService {

    // 暂按 1 = 启用，需与数据库设计核对
    private static final int USER_STATUS_ENABLED = 1;

    private final TicketMapper ticketMapper;
    private final UserMapper userMapper;
    private final TicketOperationLogMapper operationLogMapper;

    @Override
    public TicketPageResponse queryTickets(TicketQueryRequest request) {
        String keyword = request.getKeyword() == null ? null : request.getKeyword().strip();
        LambdaQueryWrapper<Ticket> query = new LambdaQueryWrapper<Ticket>()
                .select(Ticket::getId, Ticket::getTicketNo, Ticket::getTitle, Ticket::getStatus,
                        Ticket::getPriority, Ticket::getCreatorId, Ticket::getCreatedAt)
                .like(keyword != null && !keyword.isEmpty(), Ticket::getTitle, keyword)
                .eq(request.getStatus() != null, Ticket::getStatus, request.getStatus())
                .eq(request.getPriority() != null, Ticket::getPriority, request.getPriority())
                .eq(request.getCreatorId() != null, Ticket::getCreatorId, request.getCreatorId())
                .orderByDesc(Ticket::getCreatedAt, Ticket::getId);

        Page<Ticket> page = ticketMapper.selectPage(
                new Page<>(request.getPageNum(), request.getPageSize()), query);
        return new TicketPageResponse(
                page.getRecords().stream().map(this::toListItemResponse).toList(),
                page.getTotal(), page.getCurrent(), page.getSize(), page.getPages());
    }

    private TicketListItemResponse toListItemResponse(Ticket ticket) {
        TicketListItemResponse response = new TicketListItemResponse();
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
    public TicketCreateResponse createTicket(
            TicketCreateRequest request) {

        // 1. 检查创建人是否存在
        User user = userMapper.selectById(
                request.getCreatorId());

        if (user == null) {
            throw new UserNotFoundException("创建人不存在");
        }

        // 2. 检查用户状态
        if (user.getStatus() == null ||
                user.getStatus() != USER_STATUS_ENABLED) {
            throw new UserDisabledException("用户已被禁用");
        }

        // 3. 创建工单对象
        LocalDateTime now = LocalDateTime.now();

        Ticket ticket = new Ticket();
        ticket.setTicketNo(TicketNoGenerator.generate());
        ticket.setTitle(request.getTitle());
        ticket.setDescription(request.getDescription());
        ticket.setPriority(request.getPriority());
        ticket.setCreatorId(user.getId());
        ticket.setStatus(TicketStatus.OPEN);
        ticket.setVersion(0);
        ticket.setCreatedAt(now);
        ticket.setUpdatedAt(now);

        // 4. 保存工单
        int rows = ticketMapper.insert(ticket);

        if (rows != 1 || ticket.getId() == null) {
            throw new IllegalStateException("工单保存失败");
        }

        // 5. 创建工单操作日志
        TicketOperationLog log = new TicketOperationLog();

        log.setTicketId(ticket.getId());
        log.setOperatorId(user.getId());
        log.setOperationType(TicketOperationType.CREATE);
        log.setOldValue(null);
        log.setNewValue(TicketStatus.OPEN.name());
        log.setRemark("创建工单");
        log.setCreatedAt(now);

        // 6. 保存操作日志
        int logRows = operationLogMapper.insert(log);

        if (logRows != 1) {
            throw new IllegalStateException(
                    "操作日志保存失败");
        }

        // 7. 构造响应 VO
        TicketCreateResponse response =
                new TicketCreateResponse();

        response.setId(ticket.getId());
        response.setTicketNo(ticket.getTicketNo());
        response.setTitle(ticket.getTitle());
        response.setStatus(ticket.getStatus());
        response.setPriority(ticket.getPriority());
        response.setCreatorId(ticket.getCreatorId());
        response.setCreatedAt(ticket.getCreatedAt());

        return response;
    }
}
