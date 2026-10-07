package com.ticketflow;

import com.ticketflow.entity.Ticket;
import com.ticketflow.mapper.TicketMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class OptimisticLockTest {

    @Autowired
    private TicketMapper ticketMapper;

    @Test
    void testOptimisticLock() {

        // 模拟管理员 A 查询
        Ticket ticketA = ticketMapper.selectById(3L);

        // 模拟管理员 B 查询
        Ticket ticketB = ticketMapper.selectById(3L);

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

        System.out.println("B更新影响行数：" + resultB);
        System.out.println("B更新后的version：" + ticketB.getVersion());
    }
}