# TicketFlow

TicketFlow 是面向企业问题处理的 Java 工单后端。目标业务是创建工单、分配客服、处理、解决、确认关闭或重新打开，并记录操作历史。独立的 Agent 工程将在 Java 服务完成后，通过带身份及权限约束的 REST/MCP 工具调用业务能力。

## 当前进度

已补齐注册/登录/JWT、三角色及工单数据权限、统一响应与错误、OpenAPI、工单修改、分配/开始/解决/关闭/重开及事务操作日志。2026-10-20 工作副本完整 Maven 测试通过：25 个测试套件、179 次测试执行，0 失败、0 错误、0 跳过。详见[阶段进度记录](docs/day1-7-progress-2026-10-20.md)和[路线台账](docs/roadmap.md)；用户尚未宣布今天结束，这不是当天最终审查。

保留原 20 天全部范围：MySQL 优化、Redis、缓存问题、并发抢单、分布式锁、RabbitMQ 可靠性、SLA、幂等/限流、测试、压测、Docker 和面试整理。未完成项目顺延，不删除范围。

## 技术与结构

- Java 21、Spring Boot 3.5.16、Maven Wrapper。
- MyBatis-Plus 3.5.17、MySQL 8.4、Validation、Lombok、JUnit 5。
- Spring Security/JWT、OpenAPI。Redis/MQ 等后续组件尚未实现，详见路线。

```text
src/main/java/com/ticketflow/   业务代码
src/main/resources/db/         新库初始化与手动迁移脚本
src/test/                     测试代码及专用测试库结构
docs/                         路线、数据模型和接口合同
ticket-create.http            本地 HTTP 调用示例
```

当前六表为 `sys_user`、`sys_role`、`sys_user_role`、`ticket`、`ticket_comment`、`ticket_operation_log`。ER 图、索引候选、外键/删除/时间字段取舍见[数据模型](docs/data-model.md)。Notification 为后续 MQ/SLA 阶段的计划实体。

## 本地准备与启动

1. 准备 JDK 21 和独立的 MySQL 8.4 开发库。
2. 阅读 `src/main/resources/db/schema.sql`，明确目标实例后手动执行。该文件包含创建及选择 `ticketflow` 数据库的语句；不要把它用于测试库。已有库应使用所需的独立迁移脚本，不依赖 `CREATE TABLE IF NOT EXISTS` 升级已有表结构。
3. 按 `src/main/resources/application.yaml` 配置数据库环境变量。凭据保存在本机环境，不能写进源码或提交。
4. 设置必填环境变量 `TICKETFLOW_JWT_SECRET`，映射到 `ticketflow.jwt.secret`；值是至少 32 字节随机密钥的 Base64 编码，不使用测试固定值。`ticketflow.jwt.issuer` 默认为 `ticketflow`，`ticketflow.jwt.ttl` 默认为 `PT1H`（一小时）。
5. 在项目根目录运行：

```powershell
# 仅为当前终端生成密钥；不打印，不写入持久环境或文件。
$jwtBytes = New-Object byte[] 32
$jwtRandom = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try {
    $jwtRandom.GetBytes($jwtBytes)
    $env:TICKETFLOW_JWT_SECRET = [Convert]::ToBase64String($jwtBytes)
} finally {
    $jwtRandom.Dispose()
    [Array]::Clear($jwtBytes, 0, $jwtBytes.Length)
}
.\mvnw.cmd spring-boot:run
```

若已在当前终端设置要继续使用的密钥，直接执行最后一行启动。重新生成并使用不同密钥启动后，旧令牌将失效，需要重新登录。测试配置的固定密钥只用于测试。

初始代码使用 `TICKETFLOW_DB_PASSWORD`，开发数据库 URL/用户名以配置文件为准。注册 `POST /api/auth/register`，登录 `POST /api/auth/login`，从返回的 `data.accessToken` 取得令牌。调用业务接口时传入 `Authorization: Bearer <token>`；`GET /api/auth/me` 返回当前身份。详细请求字段见[接口合同](docs/api.md)。

初始化脚本只补三个启用角色 USER/AGENT/ADMIN，不预置任何用户或默认密码，不授予现有用户管理员权限。已有库可单独读取并执行 `V001__seed_builtin_roles.sql`，重复执行只补缺失角色。角色种子已写入文件，不代表本机数据库已经同步。

如需本地演示客服/管理员流程，先通过注册接口创建自己的测试账号，再在确认的开发库中手动核对用户和角色，审阅后授予所需角色。以下 SQL 是待填写示例，未由本次工作执行，不包含默认密码：

```sql
-- 先替换查询中的用户名，确认返回的账号确为自己的测试账号。
SELECT id, username, status FROM sys_user WHERE username = '<已注册的测试用户名>';
SELECT id, role_code, status FROM sys_role WHERE role_code IN ('AGENT', 'ADMIN');

-- 将 NULL 替换为刚确认的用户 ID；角色只填写本次要授予的 'AGENT' 或 'ADMIN'。
-- 保持 NULL 时不会授予任何角色。
SET @tf_bootstrap_user_id = NULL;
SET @tf_bootstrap_role_code = NULL;
INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id
FROM sys_user u
JOIN sys_role r ON r.role_code = @tf_bootstrap_role_code AND r.status = 1
WHERE u.id = @tf_bootstrap_user_id AND u.status = 1
  AND @tf_bootstrap_role_code IN ('AGENT', 'ADMIN')
  AND NOT EXISTS (
      SELECT 1 FROM sys_user_role ur WHERE ur.user_id = u.id AND ur.role_id = r.id
  );
```

这会保留注册时的 USER 角色，新增所选角色；公共注册接口不接受角色参数。账号和数据库必须由操作者明确确认，不能直接向不明账号授予 ADMIN。

## 测试

数据库集成测试使用与开发库隔离的专用数据库。当前测试配置要求 `TICKETFLOW_TEST_DB_URL`、`TICKETFLOW_TEST_DB_USERNAME`、`TICKETFLOW_TEST_DB_PASSWORD`，并关闭自动 schema 初始化；初始化应经过测试代码的目标库校验。执行前阅读测试说明与目标库保护逻辑。

```powershell
.\mvnw.cmd test
```

2026-10-20 在工作副本 `D:\Documents\ChatGPT\Java项目\ticketflow` 完成完整 Maven 测试：179 次执行、25 个套件、0 失败/错误/跳过；包含真实测试数据库中的认证、权限、状态、并发及回滚验证。源码编译和 Maven Wrapper 版本检查也通过。源码同步及原目录验证由后续步骤单独记录，这些结果不能证明开发库已迁移。原始报告位于 `target/surefire-reports`。

## 文档与接口

- [完整原路线及进度](docs/roadmap.md)
- [ER 图和数据设计决定](docs/data-model.md)
- [API 合同、角色矩阵及验收场景](docs/api.md)
- [2026-10-20 阶段进度与验证边界](docs/day1-7-progress-2026-10-20.md)

本轮接口成功格式为 `{code: 0, message, data}`，失败格式为 `{code: HTTP状态, errorCode, message, traceId}`。注册/登录之外的业务接口需要 Bearer 身份，工单创建人及操作人由服务端身份确定。文档入口为 `/v3/api-docs` 和 `/swagger-ui/index.html`，允许匿名访问；集成测试已验证 OpenAPI 的 Bearer 声明、公开登录和新增修改接口。

USER 或 ADMIN 可以创建工单。纯 AGENT 只读取和处理分配给自己的工单；兼有 USER 与 AGENT 的账号可以创建，并读取自己创建或分配给自己的工单。

原项目已同步并使用项目自带 Maven 脚本复验，179 项测试全部通过（0 失败、0 错误、0 跳过）。公开仓库为 [hyl0206/ticketflow](https://github.com/hyl0206/ticketflow)，原项目已配置该 GitHub remote；发布验证结果见阶段记录。任务工作副本的 `origin` 指向本机原仓库。本轮未执行开发库迁移，候选联合索引尚未部署或性能验证。
