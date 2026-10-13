
package com.ticketflow.service;

import com.ticketflow.dto.TicketCreateRequest;
import com.ticketflow.vo.TicketCreateResponse;

public interface TicketService {

    TicketCreateResponse createTicket(
            TicketCreateRequest request
    );
}
