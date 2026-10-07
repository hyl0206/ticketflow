package com.ticketflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ticketflow.entity.TicketOperationLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TicketOperationLogMapper
        extends BaseMapper<TicketOperationLog> {
}