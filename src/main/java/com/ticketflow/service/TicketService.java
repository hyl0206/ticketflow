
package com.ticketflow.service;

import com.ticketflow.dto.TicketCreateRequest;
import com.ticketflow.dto.TicketQueryRequest;
import com.ticketflow.vo.TicketCreateResponse;
import com.ticketflow.vo.TicketPageResponse;

public interface TicketService {

    TicketPageResponse queryTickets(TicketQueryRequest request);

    TicketCreateResponse createTicket(
            TicketCreateRequest request
    );
}
