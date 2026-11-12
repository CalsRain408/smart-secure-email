# Smart Secure Mail — 安全设计与威胁模型

> 状态标记：✅ 已实现并有测试 ｜ 🟡 部分实现 ｜ 📝 已设计、待实现
> 原则：安全即设计（Security by design）；不以"模拟成功"代替未完成功能；文档只描述真实行为。

## 1. 资产

| 资产 | 说明 |
|---|---|
| 账户凭证 | 密码（只存哈希）、会话 Cookie、CSRF token |
| 邮件内容 | 正文、主题、收件人列表（含 BCC）、附件 |
| 对端密钥 | 服务器间 HMAC 共享密钥 |
| 域隔离边界 | 每个域的数据库、Redis、附件目录 |
| 审计日志 | 安全事件记录，用于追溯 |

## 2. 信任边界与攻击者模型

```
[浏览器/客户端] ──(B1: 互联网/HTTPS)──▶ [域服务器] ──(B2: 私有网络)──▶ [DB / Redis / 附件目录]
                                           │
                                (B3: 服务器间 HTTPS + 签名)
                                           ▼
                                      [对端域服务器]
```

| 攻击者 | 能力 | 典型目标 |
|---|---|---|
| A1 匿名外部攻击者 | 可任意发 HTTP 请求 | 爆破登录、批量注册、DoS、扫描接口 |
| A2 已登录恶意用户 | 合法会话 | 越权读他人邮件/附件、滥发、XSS、伪造撤回、路径穿越 |
| A3 恶意邮件发送者 | 能给受害者发邮件（含跨域） | 钓鱼、恶意脚本、诱导 AI/快捷操作执行危险动作 |
| A4 网络攻击者 | 窃听、篡改、重放 B3 流量 | 伪造投递、重放撤回、冒充对端 |
| A5 被攻破或恶意的对端域 | 持有合法对端密钥 | 冒充第三方域发件人、越权撤回、灌入垃圾邮件 |

不在范围内：服务器主机被攻陷、客户端设备被植入恶意软件、已下载副本的删除。

## 3. STRIDE 威胁与对策

| 威胁 | 场景 | 对策 | 状态 |
|---|---|---|---|
| **S** 冒充用户 | 爆破/撞库登录 | BCrypt 哈希 ✅；错误提示不区分用户名/密码/停用 ✅；未知用户也做一次哈希比较（防时序探测）✅；按 用户名+IP 限流、连续失败临时锁定 📝 | 🟡 Phase 5 补限流 |
| **S** 冒充系统邮箱 | 注册 `admin`、`security`、`postmaster` 等地址实施钓鱼 | 保留用户名列表，与"已被占用"返回相同结果 | ✅ 有测试 |
| **S** 冒充对端服务器 | 伪造 `/federation` 请求 | 对端白名单 + HMAC-SHA256 签名（常量时间比较）+ TLS | ✅ 有测试 |
| **S** 对端冒充第三方域 | 对端 B 声称邮件来自 C 域 | `messageId` 与 `from` 的域名必须等于已认证的 Origin（`ORIGIN_MISMATCH`） | ✅ 有测试 |
| **R/T** 重放服务器间请求 | 截获一次合法投递后原样重发 | 时间戳窗口 ±300 s + nonce 900 s 内只能使用一次（`PEER_REPLAY`）+ `messageId` 幂等 | ✅ 有测试 |
| **T** 篡改传输 | 修改信封或附件 | 签名覆盖请求体摘要 ✅；`tls` profile 下 HTTPS ✅；附件 sha256 校验 📝 Phase 4 | 🟡 |
| **T** 篡改他人数据 | 修改 ID 访问他人邮件 | 身份只取自会话；所有查询带 `owner_user_id` 条件；请求体拒绝未知字段 | ✅ 有测试 |
| **R** 抵赖 | 否认发信或撤回 | 安全审计表记录注册、登录成功/失败、登出、发信 ✅；撤回、对端认证失败 📝 | 🟡 |
| **I** 信息泄露 | 错误响应泄露堆栈、SQL 或路径 | 全局异常处理只返回安全文案；`include-stacktrace: never` | ✅ |
| **I** 越权读取 | 枚举邮件/附件 ID | 所有权校验；不属于自己时统一返回 404 | ✅ 邮件（附件 📝） |
| **I** BCC 泄露 | 收件人看到密送名单 | `bcc` 与投递状态只返回给发件人本人 | ✅ 有测试 |
| **D** 拒绝服务 | 高频登录/注册/发信/上传/跨域请求 | Redis 限流 📝；请求体与附件大小上限 ✅（服务器间请求体默认 1 MiB）；nonce 只在签名校验通过后写入 ✅ | 🟡 |
| **E** 权限提升 | 普通成员调用管理接口；注册时自封管理员 | 默认拒绝 ✅；注册一律为 MEMBER，`role` 字段无法提交 ✅；方法级角色校验 📝 | 🟡 |
| **E** 存储型 XSS 窃取会话 | 邮件正文含 `<script>` | 正文按纯文本存储、以 JSON 字符串返回 ✅；HttpOnly 会话 Cookie ✅；API 与前端都有严格 CSP ✅；前端转义渲染 ✅（含跨域 e2e 测试） | ✅ |

## 4. 认证与会话（题目："登录后无需验证的交互的安全性"）✅

登录只验证一次密码，之后的请求靠会话完成认证。安全性由以下几层共同保证：

| # | 机制 | 防御的攻击 | 状态 |
|---|---|---|---|
| 1 | **密码存储**：`BCryptPasswordEncoder(12)`，库里只存哈希；密码长度限制为 8–64（BCrypt 只取前 72 字节） | 数据库泄露后还原密码 | ✅ |
| 2 | **服务端会话**：只有不透明的会话 ID 放在 Cookie `SSM_SESSION` 中，身份信息全部保存在服务端；会话 ID 只走 Cookie，不会出现在 URL（`tracking-modes: cookie`，防火墙也会拦截 `;jsessionid=`） | 篡改身份字段、URL 泄露会话 | ✅ |
| 3 | **Cookie 属性**：`HttpOnly`（JS 读不到）+ `SameSite=Strict`（跨站请求不带 Cookie）+ `Secure`（只走 HTTPS；`local` 模式关闭） | XSS 窃取会话、跨站请求伪造 | ✅（Secure 取决于 TLS，见 L1） |
| 4 | **防会话固定**：登录成功时调用 `changeSessionId()` 更换会话 ID | 攻击者预先植入会话 ID | ✅ |
| 5 | **CSRF 同步令牌**：token 存在服务端 session 中，由 `GET /auth/csrf` 下发，所有非 GET 请求必须带 `X-CSRF-TOKEN`；登录时轮换，登出时失效；每次返回值经过 XOR 掩码 | 跨站请求伪造、登录 CSRF、BREACH | ✅ |
| 6 | **每个请求都在服务端鉴权**：默认 `anyRequest().authenticated()`；行为主体只从会话的 principal 中获取，请求体中不接受任何用户 ID | 越权、伪造身份 | ✅ |
| 7 | **会话过期与登出**：空闲 30 分钟过期；登出时服务端销毁会话，旧 Cookie 立即失效 | 会话被长期盗用 | ✅ |
| 8 | **审计**：注册、登录成功/失败、登出都有记录（含来源 IP，不含密码） | 抵赖、事后追溯 | ✅ |

**设计变更记录（Phase 2）**：Phase 1 文档中计划使用"双提交 Cookie"（`CookieCsrfTokenRepository`）。实现过程中，真实 HTTP 测试发现：这种模式是无状态的，服务端只比对 Cookie 与请求头是否一致，**登录时的 token 轮换并不能让旧 token 失效**；而且如果攻击者能向浏览器写 Cookie（例如同一父域下的其他子域），就可以绕过。因此改为同步令牌模式，这也是 OWASP 推荐的首选方案。

**可复现的证明**：
- 自动化测试：`SessionCookieHttpTest`（真实 HTTP）验证了 Cookie 属性、登录后会话 ID 变化、伪造或缺失 token 返回 403、登录前的 token 在登录后失效、登出后会话失效；`AuthFlowIntegrationTest` 覆盖登录失败统一响应、会话固定、注册规则等。
- 手工演示：`scripts/e2e-local-mail.sh`。

## 5. 防爆破与防滥发（Phase 5）📝

| 对象 | 维度 | 默认策略 |
|---|---|---|
| 登录失败 | 用户名 | 15 分钟内失败 5 次 → 锁定 15 分钟（`AUTH_ACCOUNT_LOCKED`） |
| 登录请求 | IP | 每分钟 20 次 |
| 注册 | IP | 每小时 10 次 |
| 发信 | 用户 | 每分钟 10 封、每天 500 封；单封收件人 ≤ 50 |
| 附件上传 | 用户 | 每分钟 20 个 |
| 服务器间请求 | 对端域 | 每分钟 600 次 |

实现：Redis 固定窗口计数（`INCR` + `EXPIRE`）。超限返回 `429 RATE_LIMITED` + `Retry-After`。锁定与限流事件写入审计日志。用户名锁定可能被恶意利用来锁定他人账户，因此锁定时间较短，并且 IP 维度单独计数。

## 6. 输入校验与内容安全

| 风险 | 对策 | 状态 |
|---|---|---|
| 批量赋值 | Jackson `fail-on-unknown-properties: true`；DTO 与 Entity 分离（注册时提交 `role` 返回 400，有测试） | ✅ |
| 字段校验 | Bean Validation；校验失败统一返回 `VALIDATION_FAILED` 及字段明细；收件人地址使用严格语法（拒绝显示名、引号、换行注入） | ✅ |
| 审计日志注入 | 写入审计表前去除控制字符并截断 | ✅ |
| SQL 注入 | 只用 JPA / 参数化查询，禁止拼接 SQL | 约定 |
| XSS | 正文为纯文本（`body_text`），以 JSON 字符串原样返回 ✅；前端 `MailBody` 把正文作为 React 文本节点渲染（自动转义），整个前端没有使用 `dangerouslySetInnerHTML` ✅；API 响应 CSP `default-src 'none'` ✅；前端生产预览 CSP：`script-src 'self'`，禁止内联脚本 ✅。测试：Vitest（`MailBody.test.tsx`）+ Playwright（跨域发送 `<img onerror>`/`<script>` 探针：收件方看到的是原文，正文里没有生成任何元素，`window.__pwned` 未被设置） | ✅ |
| 开放重定向 | 登录后的 `?next=` 只接受以 `/` 开头、且不以 `//` 开头的站内路径 | ✅ |
| 前端令牌存储 | CSRF token 只放在内存里，不写入 localStorage 或 sessionStorage；会话凭证只在 HttpOnly Cookie 中 | ✅ |
| 点击劫持 | `X-Frame-Options: DENY` + `frame-ancestors 'none'` | ✅ |
| MIME 嗅探 | `X-Content-Type-Options: nosniff` | ✅ |
| 请求体过大 | multipart 单文件 5 MB / 请求 20 MB；Tomcat 表单 1 MB；正文 ≤ 100 000 字符（schema 约束） | ✅ |
| 恶意 schema 值 | 数据库 CHECK 约束兜底（角色、状态、文件夹） | ✅ 有测试 |
| 配置注入/路径 | 域 `id` 只允许 `[a-z0-9-]`，防止 `../` 进入数据目录路径 | ✅ 有测试 |

## 7. 附件安全（Phase 4/5）📝

1. 白名单：PNG / JPEG / GIF / WebP，按**魔数**判定。
2. 用 `ImageIO` 读取尺寸并限制（≤ 8000×8000、≤ 40 MP），防止解压炸弹。
3. 存储路径为 `<storageRoot>/blobs/<sha256 前 2 位>/<sha256>`，**完全由服务端生成**，用户文件名只作为元数据保存（会过滤控制字符）。
4. 写入前校验最终路径 `normalize()` 后仍在 `storageRoot` 之下。
5. 去重：同一域内按内容 sha256 复用物理文件，但**授权按附件元数据记录判定**，物理文件不能被直接寻址；去重从不跨域。
6. 下载：校验请求用户是该邮件的发件人或收件人；返回 `Content-Disposition: attachment` + `nosniff`。

## 8. 邮件撤回核验（Phase 5）📝

| 检查 | 失败结果 |
|---|---|
| 调用者已认证 | 401 |
| 调用者是原发件人（`sender_user_id == 当前用户`） | 404（不泄露邮件存在性） |
| 邮件状态为 `SENT` | 409 `MAIL_INVALID_STATE` |
| 在撤回窗口内（默认发送后 24 小时，可配置） | 403 `RECALL_NOT_ALLOWED` |
| 已撤回 | 幂等返回上次结果 |

- 本域收件人：在同一事务中把其 `mailbox_items.recalled` 置为 `true`，从此不再返回。
- 跨域收件人：发送签名的 `/federation/v1/recalls`；**只有对端确认 `RECALLED` 才显示"已撤回"**，否则如实显示"撤回失败/未确认"。
- 每次撤回尝试（成功、拒绝、失败）都写审计日志。
- 撤回是尽力而为：无法删除收件人已经看过或下载的内容。界面会明确提示这一点。

## 9. 钓鱼 / 垃圾邮件识别（Phase 5）📝

确定性规则打分（可单测、可解释），例如：

- 显示名冒充（显示名含其他域名或"管理员/IT 部门"等字样，但发件域不匹配）
- 正文含紧急诱导词 + 凭证索取词（"立即验证""密码""账户冻结"）
- 链接文本与实际 URL 域名不一致；使用 IP 地址或短链 URL
- 外部域首次来信 + 包含链接

得分超过阈值 → 标记 `SUSPECTED_PHISHING` 并在界面显示警示横幅。只做标记，不自动删除。

## 10. AI / 智能功能安全

- 邮件内容一律视为不可信输入；智能模块是**纯函数**，只产出建议（分类、关键词、快捷回复文本、快捷操作候选）。
- 快捷操作（如"加入日历待办"）只在用户点击确认后执行，且只影响用户自己的数据；绝不由邮件内容自动触发发送、删除或撤回。
- 默认不调用外部 AI 服务；如需接入，必须显式配置并取得用户同意。

## 11. 服务器间安全（Phase 3）✅

细节见 `protocol.md` Part B。下表对应 CLAUDE.md §7 对服务器间协议的要求：

| 要求 | 实现 | 测试 |
|---|---|---|
| 明确的信封结构 | `FederationEnvelope`（版本号、`messageId`、发件人、投递收件人、可见收件人、主题、正文、发送时间），未知字段直接拒绝 | `FederationInboundSecurityTest.invalidEnvelopeIsRejected` |
| 稳定的消息 ID | `<uuid>@<来源域>`，重试时保持不变 | `FederationDeliveryRetryTest.transientFailures…` |
| 认证对端 | 静态白名单 + `Key-Id` + HMAC-SHA256 签名；用户会话访问不了联邦端点，签名也访问不了用户 API | 未知对端 / 错误密钥 / 篡改请求体 / 两条链互相隔离 |
| 机密性与完整性 | `tls` profile：HTTPS（TLS 1.3），发送方只信任配置的 CA；签名覆盖请求体摘要 | e2e 脚本 HTTPS 模式（36 项）；篡改测试 |
| 校验收件人域名 | 不是本域 → `RECIPIENT_DOMAIN_MISMATCH` | `resultsArePerRecipient` |
| 拒绝未知对端 | `401 PEER_UNKNOWN` + 审计 `FEDERATION_AUTH_FAILED` | `unknownPeerOrKeyIdIsRejectedAndAudited` |
| 防重放与幂等 | 时间戳窗口 + nonce 一次性 + `messageId` 幂等 | `replayedRequestIsRejected`、`resendWithNewNonceIsIdempotent` |
| 明确的回执语义 | 逐收件人 `ACCEPTED/REJECTED`，事务提交后才返回；发送方区分暂时失败与永久失败 | `FederationDeliveryRetryTest`（5 种情形） |
| 防止对端伪造身份 | `ORIGIN_MISMATCH` | `peerCannotSpoofSenderOfAnotherDomain` |
| BCC 隐私 | 信封的可见头不含 BCC；接收方只把 BCC 显示给原作者，而作者在远端 | `bccRecipientIsDeliveredButNotDisclosed`、`TwoDomainFederationTest` |

**密钥管理**：每一对域共享一个对称密钥（≥ 32 字节），来源是环境变量（compose）或权限为 600 的文件（本地开发，由 `scripts/init-dev-secrets.sh` 生成），启动时校验，不写日志、不进仓库。

**恶意对端（A5）的剩余风险**：持有合法密钥的对端只能以**它自己的域**的身份发信（无法冒充第三方），但仍然可以向本域用户发送大量邮件或钓鱼邮件。对策是每对端限流和钓鱼识别，都在 Phase 5。

## 12. 日志与审计

- 绝不记录密码、会话 ID、CSRF token、对端密钥、邮件正文。
- Docker 部署使用结构化日志（ECS JSON）✅ 已配置。
- 已实现的审计事件 ✅：`USER_REGISTERED`、`AUTH_LOGIN_SUCCESS`、`AUTH_LOGIN_FAILURE`、`AUTH_LOGOUT`、`MAIL_SENT`、`MAIL_DELIVERY_FAILED`、`FEDERATION_AUTH_FAILED`、`FEDERATION_MESSAGE_RECEIVED`。审计写入使用独立事务（`REQUIRES_NEW`），业务回滚时失败事件依然保留。来源 IP 只取直连地址，不信任 `X-Forwarded-For`（当前没有反向代理）。
- 规划中的事件 📝：`AUTH_LOCKED`、`MAIL_RECALL_REQUESTED`、`MAIL_RECALL_RESULT`、`RATE_LIMIT_TRIGGERED`、`ACCESS_DENIED`、`PHISHING_FLAGGED`。

## 13. 机密管理

- 仓库内无硬编码凭证：PostgreSQL 连接信息无默认值，未设置时启动失败 ✅；`deploy/.env.example` 只含占位符，真实 `.env` 已加入 `.gitignore` ✅。
- 已禁用 Spring Boot 默认生成并打印的内存用户密码 ✅（已验证启动日志中没有）。
- 例外：`local`/`test` profile 使用嵌入式 H2，账号为 `sa`、密码为空。数据库只存在于本机文件或内存中，不对外监听。

## 14. 已知限制（当前阶段）

| # | 限制 | 计划 |
|---|---|---|
| L1 | TLS 需要显式启用：`local,domain-x,tls` 已可用（Phase 3）；默认的 `local` 模式和 compose 仍然是 HTTP（服务器间请求仍有签名，但不加密），此时 Cookie 没有 `Secure` 属性 | Phase 7：compose 挂载证书并默认启用 HTTPS |
| L2 | 会话保存在进程内存中，服务重启后所有用户需要重新登录（每个域只有一个进程，功能上是正确的） | Phase 5 接入 Spring Session + Redis |
| L3 | 限流尚未实现 | Phase 5 |
| L4 | `local`/`test` 用 H2，与 PostgreSQL 的并发与锁行为可能不同 | Phase 7 用 Testcontainers + PostgreSQL 补充集成测试 |
| L5 | JSON 请求体没有统一的字节上限（只有 multipart/表单有） | Phase 5 增加请求体大小过滤器 |
| L6 | 端到端加密暂未设计实现（加分项） | Phase 7 视进度补充设计说明 |
| L7 | 注册接口可以用来探测用户名是否存在（企业内部通讯录场景下可以接受），且目前没有限流 | Phase 5 加注册限流 |
| L8 | 发信时可以通过 `UNKNOWN_RECIPIENT` 判断本域某个用户是否存在（仅限已登录的本域用户） | 可接受：企业内同事目录本身可见 |
| L9 | 对端可以通过回执中的 `RECIPIENT_NOT_FOUND` 判断本域用户是否存在 | 可接受：对端是经过认证的可信域；协议需要这个信息才能给发件人明确的投递状态 |
| L10 | 密钥轮换：`Key-Id` 已在签名中，但每个对端只能配置一个密钥，轮换时会有短暂中断 | 后续支持同时接受新旧两个密钥 |
| L11 | 没有每对端限流，被攻破的对端可以大量投递 | Phase 5 |
| L12 | 前端 CSP 中 `style-src` 需要 `'unsafe-inline'`（Ant Design 运行时会注入 `<style>`），脚本仍然严格限制为同源 | 可接受；可以改用 antd 的静态样式抽取来去掉 |
| L13 | 当前 npm 镜像不支持 `npm audit`，前端依赖还没有做漏洞扫描 | Phase 7 换官方仓库或使用其他扫描工具 |
