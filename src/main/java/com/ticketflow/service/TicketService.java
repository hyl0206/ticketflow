
package com.ticketflow.service;

import com.ticketflow.dto.TicketCreateRequest;
import com.ticketflow.dto.TicketQueryRequest;
import com.ticketflow.dto.TicketUpdateRequest;
import com.ticketflow.vo.TicketCreateResponse;
import com.ticketflow.vo.TicketDetailResponse;
import com.ticketflow.vo.TicketPageResponse;
import com.ticketflow.vo.TicketOperationResponse;
import java.util.List;

public interface TicketService {

    TicketPageResponse queryTickets(TicketQueryRequest request);

    TicketDetailResponse getTicketById(Long id);

    TicketDetailResponse updateTicket(Long id, TicketUpdateRequest request);
    TicketDetailResponse assignTicket(Long id, Long assigneeId);
    TicketDetailResponse startTicket(Long id);
    TicketDetailResponse resolveTicket(Long id);
    TicketDetailResponse closeTicket(Long id);
    TicketDetailResponse reopenTicket(Long id);
    List<TicketOperationResponse> getOperations(Long id);

    TicketCreateResponse createTicket(
            TicketCreateRequest request
    );
}
