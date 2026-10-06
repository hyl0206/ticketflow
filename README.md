# TicketFlow

TicketFlow 是一个企业智能工单协同平台，用于实现工单创建、分配、处理、状态流转以及后续的智能化能力。

## 技术栈

- Java 21
- Spring Boot 3.5.16
- MyBatis-Plus 3.5.17
- MySQL 8.4
- Maven

## 当前核心数据模型

- `sys_user`：用户
- `sys_role`：角色
- `sys_user_role`：用户角色关系
- `ticket`：工单

## 工单状态

```text
OPEN
  ↓
ASSIGNED
  ↓
PROCESSING
  ↓
RESOLVED
  ↓
CLOSED