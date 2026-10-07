package com.ticketflow;

import com.ticketflow.entity.TicketComment;
import com.ticketflow.mapper.TicketCommentMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;

@SpringBootTest
public class TicketCommentMapperTest {

    @Autowired
    private TicketCommentMapper ticketCommentMapper;

    @Test
    void testInsertComment() {

        TicketComment comment = new TicketComment();

        comment.setTicketId(4L);
        comment.setUserId(1L);   // 改成你数据库真实存在的用户ID
        comment.setContent("正在排查图片存储服务，请稍后重试");

        int rows = ticketCommentMapper.insert(comment);

        System.out.println("影响行数：" + rows);
        System.out.println("生成的评论ID：" + comment.getId());

        TicketComment saved =
                ticketCommentMapper.selectById(comment.getId());

        System.out.println("所属工单ID：" + saved.getTicketId());
        System.out.println("评论用户ID：" + saved.getUserId());
        System.out.println("评论内容：" + saved.getContent());
        System.out.println("评论时间：" + saved.getCreatedAt());
    }
    @Test
    void testSelectCommentsByTicketId() {

        LambdaQueryWrapper<TicketComment> wrapper =
                new LambdaQueryWrapper<>();

        wrapper.eq(TicketComment::getTicketId, 4L)
                .orderByAsc(TicketComment::getCreatedAt);

        List<TicketComment> comments =
                ticketCommentMapper.selectList(wrapper);

        for (TicketComment comment : comments) {
            System.out.println(
                    "评论ID：" + comment.getId()
                            + "，用户ID：" + comment.getUserId()
                            + "，内容：" + comment.getContent()
                            + "，时间：" + comment.getCreatedAt()
            );
        }
    }
}

