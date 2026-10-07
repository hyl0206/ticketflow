package com.ticketflow;

import com.ticketflow.entity.Ticket;
import com.ticketflow.enums.TicketPriority;
import com.ticketflow.enums.TicketStatus;
import com.ticketflow.mapper.TicketMapper;
import com.ticketflow.util.TicketNoGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
public class TicketMapperTest {

    @Autowired
    private TicketMapper ticketMapper;

    @Test
    void testInsertTicket() {

        Ticket ticket = new Ticket();

        ticket.setTicketNo(TicketNoGenerator.generate()
        );
        ticket.setTitle("图片上传失败");
        ticket.setDescription("用户上传图片时服务器返回错误");

        ticket.setStatus(TicketStatus.OPEN);
        ticket.setPriority(TicketPriority.MEDIUM);

        ticket.setCreatorId(2L);
        ticket.setAssigneeId(null);

        int rows = ticketMapper.insert(ticket);

        System.out.println("影响行数：" + rows);
        System.out.println("生成的工单ID：" + ticket.getId());
        System.out.println("生成的工单编号：" + ticket.getTicketNo());

        assertNotNull(ticket.getId());

        Ticket savedTicket = ticketMapper.selectById(ticket.getId());

        assertNotNull(savedTicket);

        System.out.println("查询到的标题：" + savedTicket.getTitle());
        System.out.println("查询到的状态：" + savedTicket.getStatus());
        System.out.println("查询到的优先级：" + savedTicket.getPriority());
    }
}