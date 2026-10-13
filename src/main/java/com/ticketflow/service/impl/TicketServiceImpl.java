
package com.ticketflow.service.impl;

import com.ticketflow.dto.TicketCreateRequest;
import com.ticketflow.entity.Ticket;
import com.ticketflow.entity.TicketOperationLog;
import com.ticketflow.entity.User;
import com.ticketflow.enums.TicketOperationType;
import com.ticketflow.enums.TicketStatus;
import com.ticketflow.mapper.TicketMapper;
import com.ticketflow.mapper.TicketOperationLogMapper;
import com.ticketflow.mapper.UserMapper;
import com.ticketflow.service.TicketService;
import com.ticketflow.util.TicketNoGenerator;
import com.ticketflow.vo.TicketCreateResponse;

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
    @Transactional(rollbackFor = Exception.class)
    public TicketCreateResponse createTicket(
            TicketCreateRequest request) {

        // 1. 检查创建人是否存在
        User user = userMapper.selectById(
                request.getCreatorId());

        if (user == null) {
            throw new IllegalArgumentException("创建人不存在");
        }

        // 2. 检查用户状态
        if (user.getStatus() == null ||
                user.getStatus() != USER_STATUS_ENABLED) {
            throw new IllegalArgumentException("用户已被禁用");
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
