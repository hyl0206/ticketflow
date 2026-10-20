package com.ticketflow.vo;

import com.ticketflow.enums.TicketOperationType;
import java.time.LocalDateTime;

public record TicketOperationResponse(Long id, Long operatorId, TicketOperationType operationType,
                                      String oldValue, String newValue, String remark,
                                      LocalDateTime createdAt) {
}
