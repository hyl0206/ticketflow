-- MySQL 8.4; run against the explicitly selected TicketFlow database.
-- Manual migration: the application does not automatically execute this file.
-- Add missing built-in roles only. Preserve existing names, status and assignments.
-- No users, passwords or privileged account assignments are created.

INSERT INTO sys_role (role_code, role_name, description, status)
SELECT 'USER', '普通用户', '创建及查看自己的工单', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code = 'USER');

INSERT INTO sys_role (role_code, role_name, description, status)
SELECT 'AGENT', '客服', '查看及处理分配给自己的工单', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code = 'AGENT');

INSERT INTO sys_role (role_code, role_name, description, status)
SELECT 'ADMIN', '管理员', '查看及管理所有工单', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code = 'ADMIN');
