# API 合同与权限

本轮 Day 1–7 已实现的接口合同。2026-10-20 工作副本完整测试通过 179 次执行，详见 `docs/day1-7-progress-2026-10-20.md`；开发环境仍需配置密钥、数据库和角色。基础路径：`http://localhost:8080`。

## 响应格式

成功响应保留正确的 HTTP 状态，例如创建成功为 201：

```json
{"code": 0, "message": "成功", "data": {"id": 100, "status": "OPEN"}}
```

失败响应的 HTTP 状态与 `code` 一致，`errorCode` 是供调用方判断的稳定类别，`message` 用于说明，`traceId` 用于定位运行日志：

```json
{"code": 401, "errorCode": "UNAUTHENTICATED", "message": "请先登录", "traceId": "5c4987fa79104dca90e464bd353a2db8"}
```

以上示例不承诺某一次失败的逐字 message。当前语义：400 参数错误；401 无有效身份；403 身份有效但无权限；404 工单不存在；409 状态或并发冲突；500 未预期错误。错误类别以业务异常及统一异常处理器中的 `errorCode` 和测试为准。当前工单实现对不存在返回 404、对无权访问返回 403；尚未采用统一隐藏资源存在性的 404 策略。

## 注册、登录、当前身份

| 方法和路径 | 请求 | 成功响应 data |
|---|---|---|
| POST `/api/auth/register` | username、password、email（可选） | `{id, username, roles:["USER"]}`，201 |
| POST `/api/auth/login` | username、password | `{accessToken, tokenType:"Bearer", expiresIn, user:{id,username,roles}}` |
| GET `/api/auth/me` | Bearer 令牌 | `{id, username, roles}` |

注册用户名为 3–50 个 ASCII 字母、数字或下划线；密码为 8–72 个字符且 UTF-8 编码不超过 72 字节；email 可选，提供时必须为有效邮箱格式且不超过 100 字符。拒绝请求 DTO 未定义的字段，包括 role、roles、status、id。注册只绑定启用的 USER 角色，客户端不能自行指定管理员权限。

注册重复用户名：409 / `USERNAME_TAKEN`；没有启用的 USER 种子：503 / `REGISTRATION_UNAVAILABLE`；登录凭据错误：401 / `INVALID_CREDENTIALS`。`expiresIn` 单位是秒。

除注册、登录和明确公开的接口文档资源外，业务 API 都需要：

```http
Authorization: Bearer <access-token>
```

JWT 必填密钥环境变量为 `TICKETFLOW_JWT_SECRET`，映射到 `ticketflow.jwt.secret`；值是至少 32 字节随机密钥的 Base64 编码。`ticketflow.jwt.issuer` 默认 `ticketflow`；`ticketflow.jwt.ttl` 默认 `PT1H`，可配置范围为大于 0 且不超过 24 小时。测试固定密钥不能用于实际部署。

每次已认证请求重新读取数据库用户状态和启用角色，禁用即时生效。当前合同没有刷新令牌、服务端登出撤销或令牌黑名单；后续 Redis 阶段再评估，不将这些能力写成已完成。创建人、操作人来自可信登录身份，客户端不能用 `creatorId`、`operatorId` 或角色参数代替身份验证。

## 角色矩阵

| 动作 | USER | AGENT | ADMIN |
|---|---|---|---|
| 创建工单 | 自己 | 不单凭 AGENT 角色授权 | 自己 |
| 列表、详情、操作日志 | 自己创建的 | 分配给自己的 | 全部 |
| 修改标题/描述/优先级 | 自己的 OPEN 工单 | 不单凭 AGENT 角色授权 | 任意 OPEN 工单 |
| 分配客服 | 不允许 | 不允许 | OPEN 分配给启用的 AGENT |
| 开始、解决 | 不单凭 USER 角色授权 | 分配给自己的工单 | 任意满足状态的工单 |
| 关闭、重开 | 自己创建的工单 | 不单凭 AGENT 角色授权 | 任意满足状态的工单 |

角色权限取并集。只有 USER 或 ADMIN 可以创建工单；纯 AGENT 负责分配给自己的工单。兼有 USER 和 AGENT 的用户可以创建自己的工单，并读取“自己创建的或分配给自己的”工单。分页筛选不能绕过数据权限；`creatorId` 是筛选条件，不是身份声明。

## 工单接口

| 方法和路径 | 输入 | 结果 / 状态约束 |
|---|---|---|
| POST `/api/tickets` | title、description、priority | 201，USER 或 ADMIN 创建 OPEN；创建人来自令牌 |
| GET `/api/tickets` | pageNum、pageSize、keyword、status、priority、creatorId | 当前身份可访问的分页结果 |
| GET `/api/tickets/{id}` | 正数 id | 工单详情 VO |
| PATCH `/api/tickets/{id}` | title / description / priority 的待修改字段 | 仅 OPEN；更新与操作日志同事务 |
| POST `/api/tickets/{id}/assign` | `{ "assigneeId": 2 }` | OPEN → ASSIGNED；仅管理员 |
| POST `/api/tickets/{id}/start` | 无业务请求体 | ASSIGNED → PROCESSING |
| POST `/api/tickets/{id}/resolve` | 无业务请求体 | PROCESSING → RESOLVED；维护 resolvedAt |
| POST `/api/tickets/{id}/close` | 无业务请求体 | RESOLVED → CLOSED；维护 closedAt |
| POST `/api/tickets/{id}/reopen` | 无业务请求体 | RESOLVED → PROCESSING；保留 assigneeId，清除 resolvedAt |
| GET `/api/tickets/{id}/operations` | 正数 id | 与详情相同的数据权限；操作记录 VO |

状态枚举：`OPEN`、`ASSIGNED`、`PROCESSING`、`RESOLVED`、`CLOSED`。优先级枚举：`LOW`、`MEDIUM`、`HIGH`、`URGENT`。字符串值区分大小写。关闭后不能重新打开；没有硬删除端点。并发抢单端点 `/claim` 属于原 Day 11，不能把管理员 `/assign` 当成并发抢单已经实现。

创建示例：

```json
{"title": "电脑无法连接网络", "description": "办公室电脑无法连接公司 Wi-Fi", "priority": "HIGH"}
```

分页默认 `pageNum=1&pageSize=10`，每页最多 100 条。返回的 `data` 包含 `records`、`total`、`pageNum`、`pageSize`、`pages`。按 `createdAt`、`id` 倒序稳定排序。`keyword` 匹配标题；空白关键词不增加筛选。

创建时标题和描述不能为空，标题最多 200 字符，描述最多 10000 字符，优先级必填。PATCH 至少提供一个非 null 的可修改字段，给出的标题/描述同样不能是空白且有相同长度限制；不能通过 PATCH 直接修改状态、创建人、处理人或版本。写请求拒绝未定义字段。

业务错误类别包括 `TICKET_NOT_FOUND`、`ACCESS_DENIED`、`INVALID_TICKET_STATE`、`TICKET_CONFLICT`、`INVALID_ASSIGNEE`、`TICKET_ASSIGNEE_MISSING`。请使用类别和 HTTP 状态判断，不按中文提示文本做分支。

## 操作日志和事务

每次成功写操作应记录真实操作人、工单、操作类型、前后值、说明及时间。状态更新和日志写入必须在同一事务；日志失败则工单回滚；更新冲突不得留下成功日志。大段标题/描述不应超出现有 `old_value/new_value` 长度，具体记录策略由实现决定，文档不承诺保存完整正文历史。

当前条件更新比较服务端读取到的状态和版本，已通过两个真实事务竞争时仅一方成功、仅一条日志提交的测试。客户端接口没有 `expectedVersion` 或 `If-Match` 条件，所以不保证检测用户基于旧页面提交、但发生在上一请求完成之后的覆盖修改；这项限制不能被服务端并发测试掩盖。

业务审计日志存在数据库，应用运行日志用于排查请求，两者不能互相替代。运行日志关联 traceId、HTTP 方法、路径、状态和耗时；响应头 `X-Trace-Id` 与失败 JSON 的 traceId 一致。traceId 由服务端生成，不接受任意客户端文本写入日志。当前意外异常日志记录异常类型及 traceId，不输出可能包含 SQL 或敏感输入的异常消息。密码、完整 Bearer 令牌、签名密钥、数据库口令不写入日志；响应不暴露堆栈或数据库内部细节。

## 验收场景

- 无令牌访问业务 API；错误签名、过期令牌、禁用用户；注册重复用户名及禁止自行指定管理员角色。
- USER A 不能读取/修改 USER B 工单；纯 AGENT 不能创建且只能按分配访问；USER+AGENT 可以创建且读取并集；管理员完整访问。
- OPEN 允许修改与管理员分配；每个合法状态动作成功；跨步和 CLOSED 后动作被拒绝。
- 写入影响行数为 0 返回冲突，操作日志不提交；日志失败触发业务更新回滚。
- 原创建/分页/详情的响应包装、参数校验和排序回归。

上述核心验收已有本轮测试证据，具体覆盖和未实现边界见阶段进度记录；不将其扩展为缓存、MQ、SLA 或并发抢单已经通过。
