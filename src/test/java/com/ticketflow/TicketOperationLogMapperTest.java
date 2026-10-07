package com.ticketflow;

import com.ticketflow.entity.TicketOperationLog;
import com.ticketflow.enums.TicketOperationType;
import com.ticketflow.mapper.TicketOperationLogMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;

@SpringBootTest
public class TicketOperationLogMapperTest {

    @Autowired
    private TicketOperationLogMapper ticketOperationLogMapper;

    @Test
    void testInsertOperationLog() {

        TicketOperationLog log = new TicketOperationLog();

        log.setTicketId(4L);
        log.setOperatorId(1L);
        log.setOperationType(TicketOperationType.PRIORITY_CHANGE);
        log.setOldValue("MEDIUM");
        log.setNewValue("HIGH");
        log.setRemark("提升工单优先级");

        int rows = ticketOperationLogMapper.insert(log);

        System.out.println("影响行数：" + rows);
        System.out.println("生成的日志ID：" + log.getId());

        TicketOperationLog saved =
                ticketOperationLogMapper.selectById(log.getId());

        System.out.println("工单ID：" + saved.getTicketId());
        System.out.println("操作人ID：" + saved.getOperatorId());
        System.out.println("操作类型：" + saved.getOperationType());
        System.out.println("旧值：" + saved.getOldValue());
        System.out.println("新值：" + saved.getNewValue());
        System.out.println("备注：" + saved.getRemark());
        System.out.println("操作时间：" + saved.getCreatedAt());
    }
    @Test
    void testSelectLogsByTicketId() {

        LambdaQueryWrapper<TicketOperationLog> wrapper =
                new LambdaQueryWrapper<>();

        wrapper.eq(TicketOperationLog::getTicketId, 4L)
                .orderByAsc(TicketOperationLog::getCreatedAt);

        List<TicketOperationLog> logs =
                ticketOperationLogMapper.selectList(wrapper);

        for (TicketOperationLog log : logs) {
            System.out.println(
                    "日志ID：" + log.getId()
                            + "，操作人：" + log.getOperatorId()
                            + "，操作类型：" + log.getOperationType()
                            + "，旧值：" + log.getOldValue()
                            + "，新值：" + log.getNewValue()
                            + "，备注：" + log.getRemark()
                            + "，时间：" + log.getCreatedAt()
            );
        }
    }
}