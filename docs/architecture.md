# Smart Secure Mail — 架构设计

> 状态标记：✅ 已实现并测试 ｜ 🟡 部分实现 ｜ 📝 已设计、待实现
> 本文档描述**目标架构**，并逐项标注当前实现状态。实现进度以 `HANDOFF.md` 为准。

## 1. 设计目标与约束

| 来源 | 约束 |
|---|---|
| 题目要求 | 自研 server/client，不使用成熟邮件服务器作为核心实现 |
| 题目要求 | 同时运行两个服务器、两个隔离域名，可互相发信，存储逻辑隔离 |
| 题目要求 | 安全与稳定性为必做项；所有功能需可复现测试 |
| CLAUDE.md | 模块化单体（Modular Monolith），不拆微服务 |
| CLAUDE.md | 前后端分离；REST JSON API，前缀 `/api/v1` |

**核心决策：同一份服务端代码，按配置部署为两个独立进程**，每个进程只服务一个域名。

## 2. 总体部署视图

```
          浏览器 (React SPA, Phase 4)               浏览器
                │ HTTPS /api/v1 (Cookie 会话 + CSRF)   │
                ▼                                     ▼
 ┌──────────────────────────────┐      ┌──────────────────────────────┐
 │ server-a  (domain-a.test)    │      │ server-b  (domain-b.test)    │
 │  Spring Boot 进程            │◀────▶│  Spring Boot 进程            │
 │                              │ HTTPS │                              │
 │                              │ /federation/v1  (签名请求)          │
 └───┬───────────┬──────────┬───┘      └───┬───────────┬──────────┬───┘
     │           │          │              │           │          │
 PostgreSQL-a  Redis-a  storage-a/     PostgreSQL-b  Redis-b  storage-b/
 (私有网络 net-a)                       (私有网络 net-b)
```

- 每个域名独立拥有：进程、数据库、Redis、附件目录、用户/邮件记录、认证边界。
- Docker Compose 中 `net-a` / `net-b` 为 `internal` 网络，server-b **在网络层就无法访问** postgres-a；两个 server 仅通过 `federation` 网络互通。
- 跨域交互**只能**走 `docs/protocol.md` 定义的服务器间协议。

### 2.1 运行模式

| 模式 | 数据库 | 用途 | 状态 |
|---|---|---|---|
| `local,domain-a` / `local,domain-b` | H2 文件库（PostgreSQL 兼容模式），每域独立文件 `server/data/<domain>/db`；两域互为对端（HTTP） | 无 Docker 的本地开发与演示 | ✅ |
| `local,domain-x,tls` | 同上，浏览器 API 和服务器间通信都走 HTTPS（开发 CA，由 `scripts/gen-dev-certs.sh` 生成） | 演示传输加密 | ✅ |
| `domain-a` / `domain-b` + 环境变量 | PostgreSQL（每域独立实例），对端通过 `SSM_FEDERATION_PEERS_0_*` 配置 | 正式演示 / 验收（docker compose） | 🟡 `compose config` 校验通过，尚未实际启动 |
| `test` | H2 内存库，每个 Spring 上下文独立 | 自动化测试 | ✅ |

> H2 仅作开发/测试便利。迁移脚本保持 PostgreSQL 与 H2 双兼容；涉及 PostgreSQL 特性的行为（如并发锁）在 Phase 7 用 Testcontainers + PostgreSQL 验证。

## 3. 模块划分（后端）

包根：`com.agent.smartsecureemail`。按业务域分包，每个模块只包含必要的层（controller / service / repository / entity / dto）。

| 模块（包） | 职责 | 阶段 | 状态 |
|---|---|---|---|
| `common` | 统一响应 `ApiResponse`、错误码 `ErrorCode`、全局异常处理 | 1 | ✅ |
| `config` | 域名身份配置 `MailDomainProperties`、存储目录初始化 | 1 | ✅ |
| `security` | Spring Security 过滤链、JSON 401/403、CSRF（会话同步令牌）、安全响应头、防火墙拒绝 JSON 化、BCrypt | 1 / 2 | ✅ |
| `system` | 公开实例信息 `/api/v1/system/info` | 1 | ✅ |
| `auth` | 注册、登录、登出、`/me`、`/csrf`、会话固定防护 ✅；登录限流与锁定 📝 | 2 / 5 | 🟡 |
| `user` / `organization` | 用户、用户名规则（含保留名）、本域默认组织（启动时创建） ✅；成员管理、管理员授予 📝 | 2 | 🟡 |
| `mail` | 草稿增删改、发送、直接发送、按文件夹分页、详情、已读/星标 ✅；回复、撤回 📝 | 2 / 5 | 🟡 |
| `delivery` | 收件人路由（`RecipientRouter`：本域 / 已配置对端 / 未知用户 / 未配置域名） | 2 / 3 | ✅ |
| `federation` | `protocol`：信封、回执、HMAC 签名；`inbound`：签名认证过滤器、nonce 防重放、幂等入库；`outbound`：发件队列、签名客户端、投递器（重试/退避/永久失败） ✅；跨域撤回、每对端限流 📝 | 3 / 5 | 🟡 |
| `contact` | 联系人、联系人群组、群发展开 | 4 | 📝 |
| `attachment` | 图片附件上传/下载、签名校验、去重、隔离存储 | 4 / 5 | 📝 |
| `intelligence` | 关键词提取与分类、模糊搜索、钓鱼/垃圾识别、快捷回复、快捷操作 | 5 / 6 | 📝 |
| `audit` | 安全审计事件记录（独立事务）✅；查询与告警 📝 | 2 / 5 | 🟡 |
| `ratelimit` | 基于 Redis 的限流（登录、注册、发信、上传、服务器间） | 5 | 📝 |

模块依赖方向（只允许向下依赖）：

```
controller 层:  auth  mail  contact  attachment  federation  system
                  │     │      │         │           │
service 层:       └──▶ user/organization ◀┘    delivery ◀┘
                        mail ──▶ delivery ──▶ federation(client)
                        intelligence (被 mail 调用，纯函数式、无副作用)
横切:             security  audit  ratelimit  common  config
```

## 4. 数据模型

每个域名一个独立数据库，**没有跨域外键**；远端用户只以地址字符串出现。Schema 由 Flyway 版本化迁移管理（`server/src/main/resources/db/migration`），Hibernate 仅做 `validate`。

| 表 | 说明 | 状态 |
|---|---|---|
| `organizations` | 组织（企业工作区） | ✅ V1 |
| `users` | 本域用户；`username` 为地址本地部分；`password_hash` 只存哈希；`role ∈ {ADMIN, MEMBER}` | ✅ V1 |
| `mail_messages` | 本域已知的每封逻辑邮件（本地撰写或从对端接收）；`message_id` 全局唯一，格式 `<uuid>@<来源域>`，同时作为跨域幂等键；`sender_display_name`（V2） | ✅ V1/V2 |
| `mail_recipients` | 收件人及**按收件人**的投递状态 | ✅ V1 |
| `mailbox_items` | 某个本地用户对某封邮件的视图（文件夹、已读、星标、是否已撤回）；**所有权校验基于此表** | ✅ V1 |
| `security_audit_events` | 只追加的安全审计日志，不存密码/令牌/正文 | ✅ V1 |
| `contact_groups` / `contact_group_members` | 联系人群组 | 📝 V2+ |
| `attachments` / `attachment_blobs` | 附件元数据与按内容哈希去重的物理文件（去重范围限定在本域内） | 📝 V2+ |
| `delivery_outbox` | 跨域投递任务：每个 (邮件, 对端域) 一行；状态 PENDING/DONE/FAILED、尝试次数、下次尝试时间、最近错误 | ✅ V3 |
| `federation_nonces` | 已使用过的 (对端, nonce)，唯一约束实现防重放，过期后定时清理（幂等由 `mail_messages.message_id` 唯一约束保证，不需要单独的入站日志表） | ✅ V3 |

### 4.1 邮件状态机

邮件（`mail_messages.status`）：

```
DRAFT ──send──▶ SENT ──recall──▶ RECALLED
  ▲  │
  └──┘ edit/save
```

收件人投递状态（`mail_recipients.delivery_status`）：

```
PENDING ──本域投递成功/对端 ACK──▶ DELIVERED ──撤回确认──▶ RECALLED
   │                                   └──撤回失败/拒绝──▶ RECALL_FAILED
   ├──可重试错误──▶ RETRYING ──成功──▶ DELIVERED
   │                  └──超过最大重试/不可重试错误──▶ FAILED
   └──不可重试错误（如收件人不存在）──▶ FAILED
```

邮箱视图（`mailbox_items.folder`）：`DRAFTS`（发件人草稿）、`SENT`（发件人副本）、`INBOX`（收件人副本）、`TRASH`。

## 5. 关键流程

### 5.1 本域投递（Phase 2）✅
1. 客户端 `POST /api/v1/mails`（或草稿 `.../send`）→ 服务端从会话取得发件人（**不信任前端传入的用户 ID**）。
2. 校验收件人地址；按域名分组：本域 / 已知对端 / 未知域（拒绝）。
3. 单事务内：草稿 `mail_messages` 转为 `SENT`，发件人的 `mailbox_items` 从 `DRAFTS` 移到 `SENT`，为每个本域收件人创建 `mailbox_items(INBOX)`，收件人状态置为 `DELIVERED`。
4. **全部收件人可投递才发送**：只要有一个收件人是未知用户或外域地址，整封邮件就会被拒绝（`MAIL_RECIPIENT_INVALID`，逐地址给出原因），不会出现"部分成功"。
5. 并发重复发送同一草稿：由 `@Version` 乐观锁和 `(owner, message, folder)` 唯一约束保证只会成功一次，其余请求返回 `409`（有并发测试）。
6. 审计 `MAIL_SENT`（只记录收件人数量，不记录主题和正文）。

### 5.2 跨域投递（Phase 3）✅

```
 domain-a                                                   domain-b
 ───────────────────────────────────────────                ─────────────────────────────────────
 POST /api/v1/mails
   └ MailService.send (一个事务)
       ├ 本域收件人 → INBOX 条目, DELIVERED
       ├ 对端收件人 → 保持 PENDING
       └ MailSentEvent → OutboxEnqueuer
                         → delivery_outbox (同一事务)
 DeliveryDispatcher (每 1s 轮询)
   ① 短事务: 构造信封(只含该对端待投递收件人)
   ② FederationClient: HMAC 签名 ─── HTTPS ──────────▶  FederationAuthenticationFilter
                                                        (对端/时间戳/摘要/签名/nonce)
                                                       FederationInboundService (一个事务)
                                                        ├ Origin 一致性校验
                                                        ├ 按 messageId 幂等入库
                                                        └ 逐收件人 INBOX 条目
                         ◀──────── 逐收件人回执 ─────── (事务提交后返回)
   ③ 短事务: ACCEPTED→DELIVERED, REJECTED→FAILED,
             暂时失败→RETRYING + 退避, 次数用尽→FAILED
```

1. 发送接口只同步判断"能不能投"（本域用户是否存在、域名是否已配置），跨域收件人的最终结果是异步的，作者可以通过 `delivery[].status` 看到。
2. 每个 (邮件, 对端域) 只有一行 outbox；重试时只发送尚未确认的收件人。
3. 接收方对 `messageId` 幂等，所以"发送方崩溃后重发"和"并发重复投递"都是安全的。

### 5.3 撤回（Phase 5）📝
发件人身份校验 → 状态校验 → 策略校验（时间窗口）→ 本域收件人直接隐藏 → 跨域收件人发送签名的撤回请求 → **只有对端确认才标记 `RECALLED`** → 全程记审计。详见 security.md §7。

## 6. 前端（Phase 4）🟡 4.1 已完成

`client/`：React 18 + TypeScript（strict）+ Vite 7 + Ant Design 5 + React Router 7 + TanStack Query 5。

```
client/src/
├── api/        http.ts（fetch 封装、ApiError、会话绑定 CSRF token 管理）、endpoints.ts、types.ts、errors.ts（错误码 → 中文提示）
├── hooks/      queries.ts（TanStack Query：查询键、hooks、会话过期处理）
├── lib/        compose.ts（回复预填、地址校验）、domain.ts（域名颜色）、time.ts
├── components/ AddressTag、DomainMark、Postmark（邮戳）、DeliveryRoute（投递回执）、MailBody（纯文本正文）、RequireAuth
└── features/   auth（登录/注册）、mail（三栏界面、撰写抽屉）、settings（账户）
```

- **部署形态**：每个域名一个前端实例。开发和预览服务器把 `/api` 代理到该域名的后端，前端和 API 同源，不需要 CORS，会话 Cookie 可以保持 `SameSite=Strict`。`npm run dev:a` → :5173 → :8081；`npm run dev:b` → :5174 → :8082。
- **数据流**：组件只通过 hooks 访问服务端状态。收件箱每 15 秒轮询一次；跨域投递还在进行中（`PENDING`/`RETRYING`）时，已发送邮件的详情每 1.5 秒刷新一次，直到投递有了结果。
- **会话**：`useMe()` 把 401 视为"未登录"，而不是错误。任何请求返回 `UNAUTHENTICATED` 时，全局处理器会清空本地会话，路由守卫随即跳转到登录页。登录后的跳转地址只允许站内路径（防开放重定向）。
- **视觉设计**：方向是"挂号信"。域名标识色只用来区分地址属于哪个域；阅读区顶部用邮戳展示发件域和发送时间；已发送邮件显示逐个收件人的投递路线（本域实线、跨域虚线）。窄屏下列表和详情分页显示。

## 7. 目录结构

```
smart-secure-email/
├── CLAUDE.md / AGENTS.md      # 开发约束
├── HANDOFF.md                 # 阶段进度交接
├── docs/                      # requirements / architecture / protocol / security
├── server/                    # Spring Boot 服务端（一份代码，两个域名进程）
│   ├── pom.xml, mvnw, Dockerfile
│   └── src/main/{java,resources}, src/test
├── client/                    # React 客户端（Vite；e2e/ 为 Playwright 测试）
├── deploy/                    # docker-compose.yml, .env.example
└── scripts/                   # run-local.sh 等启动脚本
```

## 8. 主要技术决策记录

| # | 决策 | 原因 |
|---|---|---|
| D1 | Spring Boot 3.5.x（初始脚手架为 4.1.2，已降级） | CLAUDE.md 规定 Spring Boot 3 |
| D2 | 工程迁移到 `server/`，为 `client/` 预留位置 | 对应交付物 "server/client 源码"，前后端清晰分离 |
| D3 | 一份代码 + Spring Profile 区分域名 | 满足"配置化部署两个独立域名"，避免代码分叉 |
| D4 | 增加 H2 作为 `local`/`test` 数据库 | 本机暂无 Docker/PostgreSQL 时仍能启动与测试；正式路径保持 PostgreSQL |
| D5 | 浏览器认证采用服务端会话 + HttpOnly Cookie；会话 Phase 2 暂存进程内存，Phase 5 迁移到 Spring Session Redis | CLAUDE.md 推荐；凭证不暴露给 JS，可服务端即时注销；`local` 模式不依赖外部服务 |
| D6 | 服务器间认证采用 TLS + 每对端共享密钥 HMAC-SHA256 请求签名 | 实现简单、可测试、使用标准原语；mTLS 作为可选增强 |
| D7 | 跨域投递用 Transactional Outbox + 幂等 `message_id` | 保证重试安全，避免重复投递和丢信 |
| D8 | 正文以纯文本存储与渲染（暂不支持富文本 HTML） | 从源头消除存储型 XSS；如后续支持 HTML 必须白名单净化 |
| D9 | CSRF 改用会话同步令牌（`HttpSessionCsrfTokenRepository`），不再使用双提交 Cookie | 真实 HTTP 测试发现双提交 Cookie 的 token 轮换无效，且可被写 Cookie 的攻击者绕过（见 security.md §4） |
| D10 | 对客户端暴露的邮件 `id` 是 `mailbox_items.id`（每个用户自己的条目） | 所有查询都天然带 owner 条件；同一封邮件在不同用户那里的 id 不同，无法通过 id 关联他人邮件 |
| D11 | 未配置的域名一律同步拒绝（`DOMAIN_NOT_SUPPORTED`）；Phase 2 时所有外域都被拒绝，Phase 3 起已配置的对端可以投递 | 遵守"不模拟成功"原则 |
| D12 | 注册用户一律为 `MEMBER`；管理员的产生方式留到企业功能阶段 | 避免"第一个注册的人成为管理员"这类提权竞态 |
| D13 | 服务器间认证采用"共享密钥 HMAC 签名 + TLS"，而不是 mTLS | 签名在应用层校验，可单元测试，并能精确返回 `PEER_*` 错误码；TLS 负责机密性。mTLS 可作为后续增强 |
| D14 | 对端配置（含 `PeerRegistry`）放在 `config` 包，发信通过 `MailSentEvent` 通知 `federation` 写 outbox | 避免 `mail` ↔ `delivery` ↔ `federation` 之间的包循环依赖；事件同步发布，outbox 与发送在同一事务内 |
| D15 | 跨域投递异步进行，发送接口返回 `201` + `PENDING` | 远端用户是否存在只有对端知道；同步调用对端会让发送接口受对端可用性影响 |
| D16 | Phase 3 的 nonce 存在数据库表中 | 不额外引入 Redis 依赖，`local` 模式仍可零依赖运行；Phase 5 接入 Redis 时一起迁移 |
| D17 | 前端依赖锁定在成熟的大版本线（React 18、antd 5、React Router 7、Vite 7、Vitest 3、TypeScript 5），并精确锁定版本号 | npm 上的最新大版本（antd 6、React Router 8、Vite 8 等）API 变化较大，作业周期内追求稳定；以后可以统一升级 |
| D18 | 会话 Cookie 名按域名区分（`SSM_SESSION_A` / `SSM_SESSION_B`） | 浏览器的 Cookie 不区分端口；两个实例都在 localhost 上时，同名 Cookie 会互相覆盖 |
| D19 | 前端通过 Vite（开发/预览）同源代理访问 API，生产预览带严格 CSP | 前后端代码分离，部署上保持同源，不需要放开 CORS |
