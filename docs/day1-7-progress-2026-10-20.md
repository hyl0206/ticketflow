# Day 1–7 补齐阶段记录：2026-10-20

这是当前工作的检查点。用户尚未说今天结束，今天结束后的原路线最终审查仍待进行。原 20 天范围全部保留，后续 SLA、分布式锁、缓存、MQ、幂等、压测等未删除。

## 已交付并验证

| 范围 | 实际实现与验证 |
|---|---|
| 数据与文档 | 六表 ER 图、Notification 概念设计、关闭保留历史与外键/逻辑删除取舍；三角色幂等种子；三个联合索引候选实验脚本 |
| 工程合同 | Result 成功包装、分类错误和 HTTP 状态、参数/JSON 校验、请求 traceId、OpenAPI |
| 身份 | 注册、bcrypt 密码散列、登录、JWT 签发校验、当前用户；非法/过期令牌拒绝；禁用用户/角色和删除角色绑定即时生效 |
| 权限 | USER/ADMIN 可创建；USER 读自己的、AGENT 读分配给自己的、ADMIN 读全部；USER+AGENT 取并集；筛选不能放大权限 |
| 工单 | 创建、详情、分页、OPEN 修改、管理员分配、开始、解决、关闭、重开、操作历史 |
| 状态与事务 | 合法/非法转换、25 种状态对；工单更新与日志同事务；并发只有一方成功；失败日志触发回滚 |

## 本轮运行证据

先在工作副本 `D:\Documents\ChatGPT\Java项目\ticketflow` 完成源码编译、Maven Wrapper 版本检查和完整 Maven `test` 生命周期。随后将 61 个变更文件同步至原项目 `D:\Desktop\11111\ticketflow\ticketflow`，逐文件 SHA-256 核对一致，在原目录使用项目自带 `.\mvnw.cmd` 再运行完整测试。两个目录各自的本轮报告均为：

| 指标 | 实际值 |
|---|---:|
| 测试套件 | 25 |
| 测试执行次数 | 179 |
| 失败 | 0 |
| 错误 | 0 |
| 跳过 | 0 |

这些数量属于每次本轮运行，不沿用之前 92 次测试的历史结果。原目录日志为 `target/day7-original-full-tests.log`，XML 位于原目录的 `target/surefire-reports/`。主要覆盖证据：

- `AuthDatabaseIntegrationTest`：真实数据库注册、登录、当前身份、bcrypt、用户名重复、禁止角色注入、用户/角色禁用与权限撤销。
- `AuthRegistrationRollbackTest`：角色绑定失败时注册事务回滚。
- `SecurityFilterTest`、`SecurityIdentityTest`：身份过滤、非法及过期令牌、当前用户和角色边界。
- `TicketLifecycleIntegrationTest`：完整生命周期、权限、角色并集、非法动作无写入、伪造持久化字段拒绝、OpenAPI。
- `TicketConcurrentTransitionIntegrationTest`：两个真实事务同时开始处理同一工单，仅一个成功、一个冲突，最终只有一条成功操作日志。
- `TicketMutationRollbackIntegrationTest`：状态操作日志保存失败时，数据库中的状态、版本和时间回滚。
- `TicketMutationUnitTest` 及状态测试：修改与状态规则、25 种状态对和异常边界。
- 原创建、分页、详情、Mapper、乐观锁及事务回滚测试全部随完整测试回归。

单个测试可以检查多个状态组合，因此“25 种状态对”不是额外加在 179 上的测试次数。测试使用专用测试数据库，应用启动及业务请求通过测试上下文验证；不把这些结果写成开发库已经完成迁移。

## 尚未完成或不能据此宣称的事项

1. 源码已同步并在原目录验证通过；保留同步前原文件备份，Git 历史未改写。
2. 已发布到公开仓库 `https://github.com/hyl0206/ticketflow`。原项目 `origin` 指向该地址，`main` 已推送并保留原有历史；首次远程核验与本地功能提交 `6431954fce3becd0234e54b58723686e0d2a8f06` 一致，禁用凭据后的匿名 Git 读取成功。任务工作副本的 `origin` 指向本机原仓库。
3. 已使用 `ticketflow_user` 对开发库进行只读核对：当前库为 `ticketflow`，USER/AGENT/ADMIN 均存在且 status=1，工单字段与实现一致，无需补写角色种子。本轮未对开发库执行迁移或业务写入。新库/测试库种子和 V001 只补缺失角色；受信任测试账号的手工角色引导步骤在 README。
4. 三个候选联合索引没有部署，没有 EXPLAIN/压测结果，仍属于 Day 8 实验。脚本存在不等于性能优化完成。
5. `/claim` 并发抢单接口尚未实现，Day 11 仍未开始。当前验证的是状态更新的并发基础。
6. 服务端条件更新比较本次读取到的版本/状态；客户端尚未提交 `expectedVersion` 或 `If-Match`，不能防止基于旧页面、在前次更新完成后才发起的覆盖修改。
7. Notification、Redis、RabbitMQ、SLA、限流、部署和压测仍按原路线推进；关闭保留历史不等于已实现逻辑删除。
8. 实际开发/部署必须设置 `TICKETFLOW_JWT_SECRET`。测试固定密钥仅限测试，换签名密钥后旧令牌失效；当前没有刷新令牌或服务端登出撤销机制。

## 下一步

GitHub 仓库创建与首轮发布已完成，后续按原路线处理未完成项。用户明确说今天结束后，再对照原 Day 1–20 范围和 Day 1–7 清单审查事实、测试证据、剩余工作及学习掌握情况。
