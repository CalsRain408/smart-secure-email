# HANDOFF — 开发进度交接

> 每完成一个阶段（或一次重要的增量）就更新本文件。只记录**已验证**的事实；未验证的内容要明确标注。

## 当前状态

| 项 | 值 |
|---|---|
| 当前阶段 | **Phase 4.1 — 邮件客户端（核心界面）：已完成** |
| 下一阶段 | Phase 4.2 — 联系人群组、图片附件（含联邦传输）、回复关联，以及对应界面 |
| 最后更新 | 2026-11-12 |
| 后端 | Java 21 · Spring Boot 3.5.16 · Spring Security · JPA · Flyway |
| 前端 | React 18 · TypeScript 5（strict）· Vite 7 · Ant Design 5 · React Router 7 · TanStack Query 5 |
| 数据库 | PostgreSQL（正式）；H2 PostgreSQL 模式（`local` / `test`） |

## 阶段总览

| 阶段 | 内容 | 状态 |
|---|---|---|
| 1 | 项目初始化、配置、数据库迁移、Docker Compose | ✅ 完成（前端脚手架推迟到 Phase 4，见下） |
| 2 | 域配置、用户模型、认证、邮件持久化、本域投递 | ✅ 完成 |
| 3 | 服务器间协议、对端认证、跨域投递、回执与重试 | ✅ 完成（跨域撤回按计划放在 Phase 5） |
| 4 | 客户端：登录注册、收件箱/发件箱/草稿、撰写回复、群组与附件 | 🟡 4.1 完成（核心界面）；4.2 未开始（群组、附件、回复关联） |
| 5 | 限流、访问控制、附件安全、撤回核验、钓鱼识别 | ⏳ |
| 6 | 搜索、关键词、分类、快捷回复、快捷操作 | ⏳ |
| 7 | 集成/安全/并发测试、文档、演示验证 | ⏳ |

---

## Phase 1 记录（2026-11-12）

### 完成内容

**文档**
- `docs/architecture.md`：部署视图、模块划分、数据模型、状态机、关键流程、技术决策记录（D1–D8）。
- `docs/protocol.md`：客户端 API 约定（响应结构、错误码、分页、CSRF、接口清单）+ 服务器间协议 v1（HMAC 签名、防重放、信封、逐收件人回执、重试、撤回）。
- `docs/security.md`：资产、信任边界、攻击者模型、STRIDE 对策表、各项安全机制设计、已知限制。

**工程结构调整**
- Spring 工程从仓库根目录移动到 `server/`，为 `client/` 预留位置；删除了 Initializr 生成的 `HELP.md`、空的 `static/`、`templates/` 和 `application.properties`。
- Spring Boot **4.1.2 → 3.5.16**（CLAUDE.md 规定 Spring Boot 3）。

**后端骨架（`server/`）**
- `config.MailDomainProperties`：域名身份（id / name / displayName / storageRoot），启动时校验，格式不合法则启动失败。
- `config.DomainStorageInitializer`：启动时创建并校验本域私有存储目录。
- `common.api.ApiResponse` / `ApiError`：统一响应结构。
- `common.error.ErrorCode` / `ApiException` / `GlobalExceptionHandler`：集中异常处理，不向客户端泄露内部细节。
- `security.SecurityConfig`：默认拒绝；公开白名单只有 `GET /api/v1/system/info` 和 `/actuator/health`；CSRF（Cookie→Header）；JSON 401/403；CSP / X-Frame-Options / Referrer-Policy；禁用 formLogin、httpBasic 和默认生成的用户密码。
- `system.SystemController`：`GET /api/v1/system/info` 返回本实例所属的域名。
- Flyway `V1__baseline_schema.sql`：`organizations`、`users`、`mail_messages`、`mail_recipients`、`mailbox_items`、`security_audit_events`（含 CHECK 约束与索引）。
- Profile：`domain-a`（8081）、`domain-b`（8082）、`local`（H2 文件库，每域独立）、`test`。

**基础设施**
- `server/Dockerfile`：多阶段构建，运行时使用非 root 用户。
- `deploy/docker-compose.yml`：每个域独立的 PostgreSQL、Redis、server 和存储卷；`net-a` / `net-b` 为 internal 网络，两个 server 只通过 `federation` 网络互通。
- `deploy/.env.example`：只有占位符；`.gitignore` 忽略 `.env` 和 `data/`。
- `scripts/run-local.sh domain-a|domain-b`：本地一键启动。

### 验证结果（实际执行）

1. `cd server && ./mvnw test`（JDK 21）：**14 个测试，0 失败，0 错误**。
   - `MailDomainPropertiesTest`（4）：合法配置能绑定；非法域名、路径型 id（`../domain-b`）、缺少存储目录都会导致启动失败。
   - `SecurityBaselineTest`（6）：未认证 → JSON 401；无 CSRF token 的 POST → 403；公开端点只读；未知路径 → JSON 404；安全响应头齐全；health 不暴露细节。
   - `SmartSecureEmailApplicationTests`（2）：Flyway 建出全部 6 张表；CHECK 约束拒绝非法角色。
   - `SystemControllerTest`（2）：`domain-a` / `domain-b` profile 分别返回各自的域名。
2. 打包 jar 后用 `local,domain-a` 和 `local,domain-b` **同时启动两个进程**：
   - `:8081/api/v1/system/info` → `domain-a.test`；`:8082/api/v1/system/info` → `domain-b.test`
   - `:8081/api/v1/mails` → `401 UNAUTHENTICATED`（JSON）；两边 `/actuator/health` 都是 `UP`
   - 数据目录分别为 `server/data/domain-a/{db,storage}` 和 `server/data/domain-b/{db,storage}`
   - 启动日志中没有 "generated security password"
3. 不带 `local`、也未设置 `SSM_DB_URL` 时启动：进程以 exit=1 退出（fail-fast，没有静默使用默认库）。

### 未验证 / 推迟项（如实记录）

- **Docker Compose 未实际运行**：本机当前找不到 `docker` 命令。`docker-compose.yml` 和 `Dockerfile` 只是写好了，**尚未经过验证**。装好 Docker 后需要执行：
  `cp deploy/.env.example deploy/.env`（并修改密码）→ `docker compose -f deploy/docker-compose.yml --env-file deploy/.env up --build`，确认两个实例都能连上 PostgreSQL 并完成迁移。
- **前端脚手架推迟到 Phase 4**：CLAUDE.md 的 Phase 1 包含"配置前端"，但本阶段按用户要求聚焦在可启动的 Spring Boot 服务端。
- Redis 已在 compose 中声明，但应用**尚未接入**（Phase 2 接入 Spring Session，Phase 5 用于限流）。
- TLS 未启用（security.md L1）。

### Phase 1 补充（2026-11-12，IntelliJ 导入修复）

- 原因：工程移到 `server/` 后，`.idea/misc.xml` 中 Maven 的入口仍是 `$PROJECT_DIR$/pom.xml`，导致导入失败。已改为 `$PROJECT_DIR$/server/pom.xml`。
- Maven Wrapper 版本从 3.10.0 改为 **3.9.9**（与本机 Homebrew 版本一致，降低 IntelliJ 2024.3 的兼容风险）。改完后 `./mvnw test` 重新验证：14 个测试，0 失败。
- 新增共享运行配置 `.run/server domain-a (local).run.xml`、`.run/server domain-b (local).run.xml`（Application 类型，profile `local,domain-x`，工作目录 `server/`）。
- 本机已安装 OrbStack：`docker` 29.8.2，`docker compose` v5.5.0 可用；compose 部署**仍未实际验证**。

### 已知问题 / 待改进

- 未设置 `SSM_DB_URL` 时，报错信息是 `'url' must start with "jdbc"`，不够直观；可以在 Phase 2 增加启动前的配置检查，给出明确提示。
- `body_text` 用 `VARCHAR(100000)` 而不是 `TEXT`，是为了兼容 H2 和 Hibernate `validate`，同时作为正文长度上限。
- 根目录的 `AGENTS.md` 是旧版 CLAUDE.md 的副本（缺少 "Updating HANDOFF.md" 一条），需要决定是否同步。
- 仓库尚未 `git init`。

---

## Phase 2 记录（2026-11-12）

### 完成内容

| 模块 | 内容 |
|---|---|
| `organization` | 启动时（Web 服务开始接收请求之前）幂等创建本域默认组织 |
| `user` | `User` 实体；`UsernamePolicy`：3–32 位、不区分大小写、保留名（admin、postmaster、security 等） |
| `auth` | 注册（确认密码常量时间比较、强度规则、不能包含用户名、BCrypt 12）、登录（用户名或本域完整地址）、登出、`/me`、`/csrf` |
| `security` | 会话 Cookie `SSM_SESSION`（HttpOnly / SameSite=Strict / Secure）；登录时更换会话 ID 并轮换 CSRF token；**CSRF 改为会话同步令牌**（见下文"设计变更"）；防火墙拒绝的请求也返回统一 JSON |
| `mail` | 草稿增删改、发送草稿、直接发送、按文件夹分页（INBOX/SENT/DRAFTS/TRASH）、详情、已读/星标；BCC 和投递状态只对发件人可见 |
| `delivery` | `RecipientRouter`：本域活跃用户 → 投递；本域未知用户 → `UNKNOWN_RECIPIENT`；外域 → `DOMAIN_NOT_SUPPORTED` |
| `audit` | `AuditService`（独立事务）：注册、登录成功/失败、登出、发信 |
| `common` | `EmailAddress`（严格语法）、`PageResponse`、新错误码、乐观锁/唯一约束冲突 → 409 |
| Flyway | `V2__mail_sender_display_name.sql` |
| 脚本 | `scripts/e2e-local-mail.sh`：对运行中的两个实例做真实 HTTP 端到端检查 |

### 关键设计决定（详见 architecture.md D9–D12）

- **CSRF 设计变更**：原计划的双提交 Cookie 被真实 HTTP 测试证明存在缺陷（服务端不保存 token，登录轮换无效，旧 token 依然可用），已改为 `HttpSessionCsrfTokenRepository` + XOR 掩码。**前端（Phase 4）需要注意**：启动时和登录/登出后都要调用 `GET /api/v1/auth/csrf`，把 token 放在内存里，非 GET 请求带 `X-CSRF-TOKEN` 请求头。
- 跨域收件人在本阶段明确拒绝，不模拟成功。
- 发送是"全部可投递才发送"，没有部分成功。
- 会话暂存在进程内存中（Redis 留到 Phase 5，和限流一起接入）。
- 注册用户一律为 MEMBER。

### 验证结果（实际执行）

1. `cd server && ./mvnw test`：**65 个测试，0 失败，0 错误**（Phase 1 有 14 个）。新增：
   - 单元测试：`EmailAddressTest`（16，含换行注入、路径字符等畸形地址）、`RecipientNormalizerTest`（3）、`MailMessageTest`（5，状态迁移）
   - `AuthFlowIntegrationTest`（12）：完整注册→登录→me→登出流程；密码只存 BCrypt 哈希；完整地址登录；密码不一致、弱密码、重复用户名（不区分大小写）、保留名、批量赋值 `role`；错误密码与未知用户的响应完全一致并有审计记录；外域地址登录被拒；会话固定防护；无 CSRF token 的登录被拒
   - `SessionCookieHttpTest`（1，**真实 HTTP**）：Cookie 属性、登录后会话 ID 变化、token 必须和自己的会话配对、登录前的 token 在登录后失效、登出后会话失效
   - `MailFlowIntegrationTest`（13）：草稿持久化/编辑/删除；草稿可以不完整但没有收件人不能发送；本域投递；群发 + BCC 隐藏；未知收件人整封拒绝且不写入任何数据；外域被拒；已发送邮件不能编辑或重发；已读/星标；HTML 正文原样作为文本返回；**越权访问 6 种操作全部返回 404**；未登录返回 401；参数校验；**4 线程并发发送同一草稿只成功一次**
   - `SecurityBaselineTest` 新增：防火墙拒绝的请求返回 JSON
2. 打包后用 `local` profile 启动两个实例：domain-b 在已有的 Phase 1 数据库上成功执行 V2 迁移（验证了升级路径），并创建了默认组织。
3. `scripts/e2e-local-mail.sh`：**26 项检查全部通过**（注册/登录、本域投递、收件箱、草稿流程、越权 404、跨域拒绝、domain-b 用户无法登录 domain-a、无 CSRF 被拒、错误密码、登出后会话失效）。

### 测试中发现并已修复的问题

| 问题 | 发现方式 | 处理 |
|---|---|---|
| 双提交 Cookie 的 CSRF token 在登录后依然有效 | `SessionCookieHttpTest` | 改为会话同步令牌 |
| MockMvc 的安全测试支持会替换 CSRF 仓库，无法验证真实 Cookie | 测试与真实运行结果不一致 | 增加基于真实 HTTP 的测试 |
| 防火墙拒绝的请求返回 Spring 默认错误格式 | e2e 脚本 | 增加 `RequestRejectedHandler`，返回 JSON + 测试 |
| e2e 脚本在 macOS bash 3.2 下对嵌套引号做了花括号展开，JSON 请求体被拆散 | e2e 首次运行 21 项失败 | 先把请求体赋值给变量再传入（脚本问题，不是服务端问题） |

### 未完成 / 推迟项

- **限流与登录锁定**（CLAUDE.md 5.1 要求）：按工作流放在 Phase 5，目前**没有**任何限流。
- 回复、撤回、联系人群组、附件、搜索：按计划放在后续阶段。
- 删除已收到的邮件 / 移到废纸篓：数据模型支持 `TRASH`，但还没有接口。
- Docker Compose 部署：**仍未实际验证**。
- 管理员角色的授予方式：还没有设计。

### 已知问题 / 风险

- 会话在进程内存中，服务重启后需要重新登录。
- 注册接口可以探测用户名是否存在，目前没有限流（security.md L7）。
- 审计中 `MAIL_SENT` 是在 flush 之后、事务提交之前写入的（独立事务）。如果最后提交失败（概率极低），会留下一条实际上没有发生的"发送成功"审计记录。后续可以改为事务提交后再写审计。

---

## Phase 3 记录（2026-11-12）

### 完成内容

| 部分 | 内容 |
|---|---|
| 对端配置 | `config.FederationProperties` / `config.PeerRegistry`：静态白名单；密钥来自环境变量或文件（二选一）；启动时校验（≥ 32 字节、不能把本域配成对端、不能重复、必须是 http(s)）；密钥不会出现在日志中 |
| 协议 | `federation.protocol`：`FederationEnvelope`、`DeliveryReceipt`、`FederationSigner`（JDK HMAC-SHA256，常量时间比较） |
| 接收端 | 独立的 `/federation/**` 安全过滤链（无会话、无 CSRF）；`FederationAuthenticationFilter`：请求体上限 → 对端/密钥 ID → 时间戳 ±300 s → 摘要 → 签名 → nonce 防重放；`FederationInboundService`：Origin 一致性校验（防止伪造第三方域）、按 `messageId` 幂等入库、逐收件人回执；审计 `FEDERATION_AUTH_FAILED` / `FEDERATION_MESSAGE_RECEIVED` |
| 发送端 | 发信事务内通过 `MailSentEvent` 写入 `delivery_outbox`（transactional outbox）；`DeliveryDispatcher` 每秒轮询，"短事务 → 事务外 HTTP → 短事务"三步；暂时失败按退避重试，4xx 永久失败，次数用尽标记 FAILED；审计 `MAIL_DELIVERY_FAILED` |
| 路由 | `RecipientRouter`：已配置的对端走跨域投递；未配置的域名仍然同步拒绝 |
| TLS | `tls` profile（TLS 1.3/1.2）；浏览器 API 和服务器间通信都走 HTTPS；发送方只信任开发 CA；`scripts/gen-dev-certs.sh` |
| 数据库 | `V3__federation_outbox_and_nonces.sql`：`delivery_outbox`、`federation_nonces`，收件人状态新增 `NOT_TRACKED` |
| 脚本 | `scripts/init-dev-secrets.sh`（本地联邦密钥）、`scripts/gen-dev-certs.sh`（开发 CA 和证书）、`run-local.sh` 支持 `tls` 参数、e2e 脚本增加 A↔B 互发并支持 HTTPS |
| 部署 | compose 中为两个 server 配置对端（`SSM_FEDERATION_PEERS_0_*`），`.env.example` 增加 `FEDERATION_SECRET_A_B` |

### 验证结果（实际执行）

1. `cd server && ./mvnw test`：**101 个测试，0 失败，0 错误**（Phase 2 结束时是 65 个）。新增：
   - `FederationSignerTest`（3）：路径、来源域、时间戳、nonce、请求体、密钥任一变化都会导致签名失效
   - `PeerRegistryTest`（6）：配置错误时拒绝启动
   - `FederationInboundSecurityTest`（15）：合法投递、重复投递幂等、逐收件人结果、BCC 不泄露；篡改请求体、错误密钥、未知对端或密钥 ID（有审计）、过期或未来的时间戳、**原样重放**、缺少签名头、超大请求体；**对端伪造第三方域发件人或 messageId**；非法信封；用户会话访问联邦端点被拒、联邦签名头访问用户 API 被拒
   - `FederationDeliveryRetryTest`（6，用 JDK `HttpServer` 写的假对端）：本域即时送达、跨域先 PENDING 后 DELIVERED 且信封签名正确；503→500→成功共 3 次请求，nonce 各不相同、messageId 保持不变；401 永久失败且不重试；一直 503 时 3 次后放弃；部分收件人被拒；未配置的域名不会发出请求
   - `TwoDomainFederationTest`（5，**两个独立的 Spring 应用，各自有独立端口和数据库**）：各自返回自己的域名；A→B 并且 B→A；跨域群发（TO/CC/BCC 混合本域和对端），BCC 不泄露；远端不存在的用户异步变为 FAILED；domain-b 的用户无法在 domain-a 登录
   - `AuthFlowIntegrationTest` 新增 1 个：6 线程并发注册同一用户名，恰好成功一个，其余返回 409
2. 真实进程（打包后的 jar，`local` profile，已有的本地数据库从 V2 升级到 V3）：
   - HTTP 模式：`scripts/e2e-local-mail.sh` **36 项全部通过**（新增：A→B 投递并显示 DELIVERED、carol 在 domain-b 收到、发件人显示正确、B→A 回复、远端不存在的用户最终 FAILED、未签名的联邦请求返回 401）
   - **HTTPS 模式**（`local,domain-x,tls`）：同一个脚本 **36 项全部通过**；协商结果为 TLS 1.3；用 HTTP 访问 HTTPS 端口返回 400；不信任开发 CA 的客户端连接失败（curl 退出码 60）
3. `docker compose config`：语法和变量展开通过（**没有实际启动**）。

### 测试中发现并已修复的问题

| 问题 | 发现方式 | 处理 |
|---|---|---|
| `NonceService` 在 `@Transactional` 方法内部捕获唯一约束异常，导致事务被标记为只能回滚，重放请求返回 500 而不是 409 | `replayedRequestIsRejected` | 改为在 `REQUIRES_NEW` 事务外部捕获 |
| `AuthService.register` 有同样的问题：并发注册同一用户名会返回 500 | 排查上一个问题时审查代码发现 | 改为在控制器中捕获并返回 409，补充并发测试 |
| 测试中把 `peers[0]` 的字段分散在两个属性来源里，Spring Boot 只取优先级最高来源中的整个列表，导致配置不完整 | `FederationDeliveryRetryTest` 上下文启动失败 | 所有 peer 字段放到同一个属性来源（这一点对生产配置同样适用，已写进测试注释） |

### 未完成 / 推迟项

- **跨域撤回**（protocol.md B.4）：Phase 5，和撤回核验一起做。
- **每对端限流**：Phase 5。
- 附件随信封传输：Phase 4。
- 密钥轮换（同时接受新旧两个 `Key-Id`）：还没有实现。
- compose 默认仍为 HTTP；**compose 仍然没有实际启动过**。

### 已知问题 / 风险

- 跨域投递是异步的：默认轮询间隔 1 s，正常情况下几百毫秒到 1 秒左右送达；对端不可用时按 30s/1m/2m/5m/15m/30m 退避，大约 53 分钟后标记为 FAILED。
- 投递器运行在单个调度线程上，依赖"每个域只有一个进程"这一部署前提。如果以后要横向扩展，需要给 outbox 增加行级锁定（如 `SELECT … FOR UPDATE SKIP LOCKED`）。
- 收到的邮件里，`sentAt` 直接采用对端提供的时间。
- 恶意但持有合法密钥的对端可以向本域灌入邮件（只能以它自己的域名身份），需要 Phase 5 的限流和钓鱼识别来缓解。

---

## Phase 4.1 记录（2026-11-12）

### 完成内容

**前端 `client/`**
- 工程：Vite 7 + React 18 + TypeScript（strict、`noUncheckedIndexedAccess`）+ antd 5 + React Router 7 + TanStack Query 5；版本精确锁定（决策见 architecture.md D17）。
- 每个域名一个前端实例：`npm run dev:a`（:5173 → :8081）/ `npm run dev:b`（:5174 → :8082），同源代理，不需要 CORS。
- API 层：统一的 `ApiError`；**CSRF token 只放在内存里**，非 GET 请求自动带上，登录/登出时重置，遇到 403 自动换新 token 并重试一次；错误码映射成中文提示，`MAIL_RECIPIENT_INVALID` 会逐个地址说明原因。
- 页面：登录、注册（字段规则与服务端一致）、三栏邮件界面（收件箱/已发送/草稿箱，分页、未读标记、15 秒自动刷新）、邮件详情（邮戳 + 发件人/收件人/抄送/密送 + **逐收件人投递回执，跨域投递进行中时自动刷新**）、撰写抽屉（新建/继续编辑草稿/回复/回复全部；关闭时如有未保存修改会先确认；删除草稿需要二次确认）、账户页。
- 安全：正文只按纯文本渲染；登录后的 `?next=` 只允许站内路径；生产预览带严格 CSP 和其他安全响应头；收到 `UNAUTHENTICATED` 后统一清空本地会话并跳转登录页。
- 无障碍与响应式：表单字段都有可见标签；收件人输入框去掉了重复的 aria-label；键盘焦点可见；动画遵循"减少动态效果"设置；窄屏下列表和详情分页显示，没有横向滚动。

**设计**（按 frontend-design 插件的指南手动执行：先出方案，对照需求审查后修订，写完再截图自查）
- 方向是"挂号信"：墨蓝 / 信封灰 / 纸白 / 封印绿（主色）；域名标识色（domain-a 钴蓝、domain-b 梅紫）；三种投递状态色；字体为 IBM Plex Sans + 苹方/思源黑体。
- 唯一的大胆元素是阅读区的**邮戳**和**投递路线**；登录页用大号字体排出当前域名。
- 注意：插件安装后**没能在会话中加载**，所以是读取插件的 SKILL.md、按其中的流程手动执行的。

**后端改动**
- 会话 Cookie 名按域名区分：`SSM_SESSION_A` / `SSM_SESSION_B`（原因见 D18：浏览器 Cookie 不区分端口）；`AuthController` 登出时清除配置中的那个 Cookie 名。

### 验证结果（实际执行）

1. 后端 `./mvnw test`：**101 个测试，0 失败**（改了 Cookie 名之后重新跑过）。
2. 前端 `npm run typecheck`：通过；`npm run build`：通过（JS gzip 后约 287 KB）。
3. 前端 `npm test`（Vitest）：**30 个测试，0 失败**：
   - `http.test.ts`（8）：GET 不取 token；token 只取一次并附加到请求上；重置后重新获取；403 时恰好重试一次，失败则放弃；错误结构映射；非 JSON 的 5xx；网络错误
   - `errors.test.ts`（4）、`compose.test.ts`（11：回复/回复全部不包含自己和密送、不叠加 Re:、地址校验）
   - `MailBody.test.tsx`（2）：`<script>`/`<img onerror>` 只显示为文本，不生成任何元素
   - `DeliveryRoute.test.tsx`（2）、`LoginPage.test.tsx`（3：显示当前域名、错误密码的提示、登录请求带 CSRF token 并跳转）
4. 端到端 `npm run e2e`（Playwright，**真实浏览器 + 两个真实后端**）：**3 个测试全部通过**：
   - A 域注册 → B 域注册 → A 通过界面发信给 B（正文带 XSS 探针）→ A 看到回执变成"已送达" → B 在收件箱打开邮件，探针按原文显示，**没有生成元素，也没有被执行** → B 回复 → A 收到回复
   - 发给本域不存在的用户：逐地址提示"本域没有这个用户" → 保存草稿 → 在草稿箱找到 → 退出登录后访问收件箱会被重定向到登录页
   - 390×844 手机尺寸：一次只显示一个窗格，没有横向滚动
   - 截图保存在 `client/e2e/screenshots/`（已加入 .gitignore，每次运行重新生成）
5. 生产构建 + `vite preview`（严格 CSP）：页面正常渲染，能通过代理拿到数据，**控制台没有 CSP 违规**。

### 过程中发现并修复的问题

| 问题 | 发现方式 | 处理 |
|---|---|---|
| 同一主机上的两个实例使用同名会话 Cookie，在一个浏览器里会互相覆盖 | 设计前端部署方式时分析发现 | Cookie 名按域名区分 |
| `loadEnv(mode, cwd, '')` 会把整个进程环境变量读进 Vite 配置，调试日志里出现了令牌类变量 | `vite build --debug` 输出 | 改为只读取 `VITE_` 前缀的变量 |
| 页面高度没有撑满视口 | 截图自查 | antd `<App>` 外层容器补上 100% 高度 |
| 收件人输入框同一个标签对应两个元素（无障碍问题） | Playwright 定位器严格模式报错 | 去掉多余的 `aria-label`，改由 Form.Item 的 label 关联 |
| 列表行悬停时继承了全局链接颜色；邮戳文字太小；手机端顶栏换行 | 截图自查 | 样式修正 |
| Playwright 自带的 Chromium 下载失败（TLS 证书链中有自签名证书，疑似网络中有 HTTPS 拦截） | 安装日志 | **没有关闭 TLS 校验**，改用本机已安装的 Google Chrome（`channel: 'chrome'`） |

### 未完成 / 推迟项

- **4.2**：联系人群组（后端 + 界面）、图片附件（上传、魔数校验、存储隔离、随联邦信封传输、下载授权）、回复关联（`inReplyTo`）。
- **账户页目前只读**：后端还没有修改显示名称和修改密码的接口。
- 快捷回复推荐、快捷操作：Phase 6。
- `npm audit` 在当前镜像下不可用，依赖漏洞扫描还没做（security.md L13）。
- 目前没有把前端打包进 Docker Compose（compose 里只有后端）。

### 已知问题 / 风险

- 本机 Vitest 首次运行时出现过无输出卡住的情况，调整 `loadEnv` 前缀并清理残留进程后恢复正常，整套测试约 3 秒。根本原因没有确定，如果再出现，先检查有没有残留的 vitest 进程。
- 前端 JS 包约 890 KB（gzip 后 287 KB），主要是 antd，暂未做代码分割。

## 如何运行

```bash
# 1) 后端：两个终端（首次运行会自动生成本地联邦密钥）
scripts/run-local.sh domain-a    # :8081
scripts/run-local.sh domain-b    # :8082

# 2) 前端：再开两个终端（首次运行先 cd client && npm install）
cd client && npm run dev:a       # http://localhost:5173 → domain-a
cd client && npm run dev:b       # http://localhost:5174 → domain-b
# 在两个浏览器窗口（或一个普通窗口 + 一个无痕窗口）里分别注册 A、B 的用户，然后互相发信

# HTTPS 模式（后端）
scripts/gen-dev-certs.sh                 # 只需执行一次
scripts/run-local.sh domain-a tls        # https://localhost:8081
scripts/run-local.sh domain-b tls        # https://localhost:8082
# 前端代理到 HTTPS 后端（未验证）：VITE_API_TARGET=https://localhost:8081 NODE_EXTRA_CA_CERTS=../server/data/certs/ca.crt npm run dev:a

# 测试
cd server && ./mvnw test                 # 后端 101 个
cd client && npm test                    # 前端单元测试 30 个
cd client && npm run e2e                 # 端到端 3 个（需要先启动两个后端；会自动启动两个前端）
scripts/e2e-local-mail.sh                # API 层端到端（需要先启动两个后端）

# Docker（PostgreSQL 正式路径，尚未实际启动验证；目前只包含后端）
cp deploy/.env.example deploy/.env       # 修改所有密码和 FEDERATION_SECRET_A_B
docker compose -f deploy/docker-compose.yml --env-file deploy/.env up --build
```

**IntelliJ**：`.run/` 下的两个运行配置使用 `local` profile。首次运行前需要先执行一次 `scripts/init-dev-secrets.sh`，否则启动时会提示缺少密钥文件。

## Phase 4.2 建议的起点

1. **联系人群组**：Flyway V4（`contact_groups`、`contact_group_members`，按所有者隔离）；增删改查 API（只能操作自己的群组）；撰写时可以选择群组，**在服务端展开**成收件人（群发）；前端增加"联系人与群组"页面（CLAUDE.md 要求的页面之一）。
2. **图片附件**：Flyway V5（附件元数据 + 按 sha256 去重的物理文件，去重只在本域内进行）；`POST /api/v1/attachments`（multipart，只允许 PNG/JPEG/GIF/WebP，按魔数校验，限制尺寸和像素，存储 ID 由服务端生成，路径校验在 storageRoot 之下）；撰写时引用附件 ID；下载时校验所有权（发件人或收件人），带 `Content-Disposition: attachment` + `nosniff`；联邦信封加入 `attachments` 字段（Base64 + sha256 校验，收件方重新校验魔数）；前端：上传控件、附件列表、图片预览（使用 blob URL，CSP 已允许 `blob:`）。
3. **回复关联**：撰写请求增加 `inReplyTo`（服务端校验被回复的邮件对当前用户可见），联邦信封里已经有这个字段。
4. **账户**：修改显示名称、修改密码（需要验证旧密码；修改后让其他会话失效）。
5. 测试：附件安全测试（伪造扩展名、路径穿越文件名、超大图片、越权下载、跨域传输后完整性校验）、群组授权测试，以及 e2e 增加"带图片附件跨域发信"。
