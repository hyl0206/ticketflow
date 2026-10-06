package com.ticketflow;

import com.ticketflow.entity.User;
import com.ticketflow.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
public class UserMapperTest {

    @Autowired
    private UserMapper userMapper;

    @Test
    void testInsertUser() {

        User user = new User();

        user.setUsername("test_user_" + System.currentTimeMillis());
        user.setPassword("test_password");
        user.setEmail("test@example.com");
        user.setStatus(1);

        int rows = userMapper.insert(user);

        System.out.println("影响行数：" + rows);
        System.out.println("生成的用户ID：" + user.getId());

        assertNotNull(user.getId());
        User savedUser = userMapper.selectById(user.getId());

        assertNotNull(savedUser);

        System.out.println("查询到的用户名：" + savedUser.getUsername());
        System.out.println("查询到的邮箱：" + savedUser.getEmail());
    }
}
