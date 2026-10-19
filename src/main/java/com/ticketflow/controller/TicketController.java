
package com.ticketflow.controller;

import com.ticketflow.dto.TicketCreateRequest;
import com.ticketflow.dto.TicketQueryRequest;
import com.ticketflow.service.TicketService;
import com.ticketflow.vo.TicketCreateResponse;
import com.ticketflow.vo.TicketDetailResponse;
import com.ticketflow.vo.TicketPageResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    @GetMapping
    public TicketPageResponse queryTickets(@Valid @ModelAttribute TicketQueryRequest request) {
        return ticketService.queryTickets(request);
    }

    @GetMapping("/{id}")
    public TicketDetailResponse getTicketById(@PathVariable("id") Long id) {
        return ticketService.getTicketById(id);
    }

    @PostMapping
    public ResponseEntity<TicketCreateResponse> createTicket(
            @Valid @RequestBody TicketCreateRequest request) {

        TicketCreateResponse response =
                ticketService.createTicket(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
}
