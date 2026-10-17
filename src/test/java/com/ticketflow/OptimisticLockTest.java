package com.ticketflow;

import com.ticketflow.entity.Ticket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class OptimisticLockTest extends TicketServiceTestSupport {

    @Test
    void testOptimisticLock() {

        Long ticketId = ticketService.createTicket(request()).getId();

        // 模拟管理员 A 查询
        Ticket ticketA = ticketMapper.selectById(ticketId);

        // 模拟管理员 B 查询
        Ticket ticketB = ticketMapper.selectById(ticketId);

        System.out.println("A读取的version：" + ticketA.getVersion());
        System.out.println("B读取的version：" + ticketB.getVersion());

        // A 先修改
        ticketA.setTitle("管理员A修改");
        int resultA = ticketMapper.updateById(ticketA);

        System.out.println("A更新影响行数：" + resultA);
        System.out.println("A更新后的version：" + ticketA.getVersion());

        // B 再拿旧版本修改
        ticketB.setTitle("管理员B修改");
        int resultB = ticketMapper.updateById(ticketB);

        assertEquals(1, resultA);
        assertEquals(0, resultB);
        assertEquals(1, ticketMapper.selectById(ticketId).getVersion());
        assertEquals("管理员A修改", ticketMapper.selectById(ticketId).getTitle());

        System.out.println("B更新影响行数：" + resultB);
        System.out.println("B更新后的version：" + ticketB.getVersion());
    }

    @Override
    @AfterEach
    void cleanOwnData() {
        if (creator != null) {
            jdbc.update("DELETE FROM ticket WHERE creator_id = ?", creator.getId());
        }
        super.cleanOwnData();
    }
}
