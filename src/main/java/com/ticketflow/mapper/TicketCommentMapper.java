package com.ticketflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ticketflow.entity.TicketComment;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TicketCommentMapper
        extends BaseMapper<TicketComment> {
}