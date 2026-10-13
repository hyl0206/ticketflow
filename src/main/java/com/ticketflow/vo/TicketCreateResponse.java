
package com.ticketflow.vo;

import com.ticketflow.enums.TicketPriority;
import com.ticketflow.enums.TicketStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TicketCreateResponse {

    private Long id;

    private String ticketNo;

    private String title;

    private TicketStatus status;

    private TicketPriority priority;

    private Long creatorId;

    private LocalDateTime createdAt;
}
