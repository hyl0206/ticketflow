package com.ticketflow.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class TicketPageResponse {

    private List<TicketListItemResponse> records;
    private long total;
    private long pageNum;
    private long pageSize;
    private long pages;
}
