package com.ticketflow.controller;

import com.ticketflow.common.Result;
import com.ticketflow.dto.*;
import com.ticketflow.service.TicketService;
import com.ticketflow.vo.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
@Tag(name = "工单", description = "所有操作均基于当前登录身份与工单数据权限")
public class TicketController {
    private final TicketService ticketService;

    @GetMapping
    @Operation(summary = "按当前用户权限分页筛选工单")
    public Result<TicketPageResponse> queryTickets(@Valid @ModelAttribute TicketQueryRequest request) {
        return Result.success(ticketService.queryTickets(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询有权访问的工单详情")
    public Result<TicketDetailResponse> getTicketById(@PathVariable("id") Long id) {
        return Result.success(ticketService.getTicketById(id));
    }

    @PostMapping
    @Operation(summary = "以当前登录用户身份创建工单")
    public ResponseEntity<Result<TicketCreateResponse>> createTicket(
            @Valid @RequestBody TicketCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(Result.success(ticketService.createTicket(request)));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "修改尚未分配工单的标题、描述或优先级")
    public Result<TicketDetailResponse> update(@PathVariable("id") Long id,
                                              @Valid @RequestBody TicketUpdateRequest request) {
        return Result.success(ticketService.updateTicket(id, request));
    }

    @PostMapping("/{id}/assign")
    @Operation(summary = "管理员将待分配工单分配给启用的客服")
    public Result<TicketDetailResponse> assign(@PathVariable("id") Long id,
                                              @Valid @RequestBody TicketAssignRequest request) {
        return Result.success(ticketService.assignTicket(id, request.assigneeId()));
    }

    @PostMapping("/{id}/start")
    public Result<TicketDetailResponse> start(@PathVariable("id") Long id) {
        return Result.success(ticketService.startTicket(id));
    }

    @PostMapping("/{id}/resolve")
    public Result<TicketDetailResponse> resolve(@PathVariable("id") Long id) {
        return Result.success(ticketService.resolveTicket(id));
    }

    @PostMapping("/{id}/close")
    public Result<TicketDetailResponse> close(@PathVariable("id") Long id) {
        return Result.success(ticketService.closeTicket(id));
    }

    @PostMapping("/{id}/reopen")
    public Result<TicketDetailResponse> reopen(@PathVariable("id") Long id) {
        return Result.success(ticketService.reopenTicket(id));
    }

    @GetMapping("/{id}/operations")
    public Result<List<TicketOperationResponse>> operations(@PathVariable("id") Long id) {
        return Result.success(ticketService.getOperations(id));
    }
}
