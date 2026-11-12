# Smart Secure Mail — 协议说明

> 状态标记：✅ 已实现并测试 ｜ 📝 已设计、待实现
> 本系统**不实现 SMTP/IMAP**，也不声称与其兼容。客户端—服务器、服务器—服务器均使用下文定义的应用层 HTTP(S)+JSON 协议。

本文分两部分：

- **Part A** 客户端 API（浏览器 ↔ 本域服务器），前缀 `/api/v1`
- **Part B** 服务器间协议（域 A ↔ 域 B），前缀 `/federation/v1`（✅ 投递已实现）

---

# Part A：客户端 API

## A.1 通用约定

| 项 | 约定 |
|---|---|
| 传输 | HTTPS（本地 `local` 模式为 HTTP，见 security.md 已知限制） |
| 格式 | `Content-Type: application/json; charset=utf-8`；附件上传使用 `multipart/form-data` |
| 时间 | ISO-8601 UTC，如 `2026-11-12T06:53:07.789Z` |
| 未知字段 | 请求体包含未声明字段时拒绝（`MALFORMED_REQUEST`），防止批量赋值 |
| 身份 | 一律从服务端会话取得，**请求中不接受 userId 之类的身份字段** |

### A.1.1 统一响应结构 ✅

成功：

```json
{ "success": true, "data": { "...": "..." }, "timestamp": "2026-11-12T06:53:07.789Z" }
```

失败：

```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_FAILED",
    "message": "Request validation failed",
    "details": [ { "field": "subject", "reason": "must not be blank" } ]
  },
  "timestamp": "2026-11-12T06:53:07.789Z"
}
```

- `error.code`：稳定的机器可读错误码；客户端按 code 处理，不解析 message。
- `error.message`：可直接展示的安全文案，**不含**堆栈、SQL、文件路径或其他用户的数据。
- `details`：仅在字段校验失败时出现。

### A.1.2 分页 ✅

列表接口统一使用查询参数 `page`（从 0 开始）、`size`（默认 20，最大 100），响应 `data`：

```json
{ "items": [ ], "page": 0, "size": 20, "totalElements": 135, "totalPages": 7 }
```

`page < 0` 或 `size` 不在 1–100 范围内返回 `400 VALIDATION_FAILED`。排序由服务端固定（收件箱/发件箱按发送时间倒序，草稿按更新时间倒序），不接受客户端传入的排序字段。

### A.1.3 认证与 CSRF ✅

- **会话**：登录成功后服务端下发会话 Cookie（`HttpOnly; Secure; SameSite=Strict`，`local` 模式下没有 `Secure`）。Cookie 名按域名区分：domain-a 为 `SSM_SESSION_A`，domain-b 为 `SSM_SESSION_B`，默认为 `SSM_SESSION`，避免同一主机上的两个实例互相覆盖会话。会话存储在服务端，空闲 30 分钟过期；会话 ID 只通过 Cookie 传递，不会出现在 URL 中。
- **CSRF（同步令牌模式）**：token 存在服务端 session 中，**不使用 Cookie 传递**。
  1. 客户端启动时调用 `GET /api/v1/auth/csrf`，得到 `{headerName: "X-CSRF-TOKEN", token}`，把 token 保存在内存中；
  2. 之后所有非 GET 请求（**包括注册和登录**）都带上请求头 `X-CSRF-TOKEN: <token>`；
  3. **登录成功后 token 会轮换**，客户端必须重新调用 `/auth/csrf` 获取新 token；登出后旧 token 随 session 一起失效。
  4. 每次返回的 token 值都经过 XOR 掩码处理，值会变化，但都对应同一个服务端 token（用于防 BREACH 攻击）。
- 未认证 → `401 UNAUTHENTICATED`；已认证但无权限，或 CSRF 校验失败 → `403 FORBIDDEN`。
- 被安全防火墙拦截的畸形请求（如 `//`、`;jsessionid=`、编码后的 `..`）→ `400 MALFORMED_REQUEST`。

## A.2 错误码

| code | HTTP | 含义 | 状态 |
|---|---|---|---|
| `VALIDATION_FAILED` | 400 | 字段或参数校验失败 | ✅ |
| `MALFORMED_REQUEST` | 400 | 请求体无法解析、含未知字段，或 URL 被防火墙拒绝 | ✅ |
| `UNAUTHENTICATED` | 401 | 未登录或会话失效 | ✅ |
| `FORBIDDEN` | 403 | 无权限 / CSRF 校验失败 | ✅ |
| `NOT_FOUND` | 404 | 资源不存在，**或不属于当前用户**（不区分两者，避免资源枚举） | ✅ |
| `METHOD_NOT_ALLOWED` | 405 | 方法不支持 | ✅ |
| `CONFLICT` | 409 | 并发修改冲突（乐观锁）或唯一约束冲突 | ✅ |
| `PAYLOAD_TOO_LARGE` | 413 | 请求或附件过大 | ✅ |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | 不支持的内容类型 | ✅ |
| `RATE_LIMITED` | 429 | 触发限流；响应头带 `Retry-After` | ✅（码已定义，限流 Phase 5） |
| `INTERNAL_ERROR` | 500 | 服务器内部错误（详情只写服务端日志） | ✅ |
| `AUTH_INVALID_CREDENTIALS` | 401 | 用户名或密码错误、账户停用、外域地址（统一响应，不区分原因） | ✅ |
| `AUTH_ACCOUNT_LOCKED` | 423 | 连续失败导致临时锁定 | 📝 |
| `AUTH_PASSWORD_MISMATCH` | 400 | 注册时两次密码不一致 | ✅ |
| `AUTH_USERNAME_UNAVAILABLE` | 409 | 用户名已被占用**或**属于保留名（两者返回相同结果） | ✅ |
| `MAIL_RECIPIENT_INVALID` | 400 | 收件人无法投递，或发送时没有收件人；`details[].field` 为地址，`reason` 为 `UNKNOWN_RECIPIENT`（本域用户不存在）/ `DOMAIN_NOT_SUPPORTED`（未配置的域名） | ✅ |
| `MAIL_INVALID_STATE` | 409 | 当前状态不允许该操作（如编辑或重复发送已发送的邮件） | ✅ |
| `RECALL_NOT_ALLOWED` | 403 | 非原发件人 / 超出撤回窗口 / 状态不允许 | 📝 |
| `ATTACHMENT_REJECTED` | 400 | 附件类型、签名或尺寸不合规 | 📝 |

## A.3 接口清单

| 方法 | 路径 | 说明 | 认证 | 状态 |
|---|---|---|---|---|
| GET | `/api/v1/system/info` | 实例所属域名与版本 | 公开 | ✅ |
| GET | `/actuator/health` | 健康检查（不含细节） | 公开 | ✅ |
| GET | `/api/v1/auth/csrf` | 获取当前会话的 CSRF token | 公开 | ✅ |
| POST | `/api/v1/auth/register` | 注册 | 公开（限流 Phase 5） | ✅ |
| POST | `/api/v1/auth/login` | 登录 | 公开（限流 Phase 5） | ✅ |
| POST | `/api/v1/auth/logout` | 登出，销毁会话 | 是 | ✅ |
| GET | `/api/v1/auth/me` | 当前用户 | 是 | ✅ |
| GET | `/api/v1/mails?folder=INBOX\|SENT\|DRAFTS\|TRASH&page&size` | 邮件列表（不含已撤回） | 是 | ✅ |
| GET | `/api/v1/mails/{id}` | 邮件详情（校验所有权） | 是 | ✅ |
| PATCH | `/api/v1/mails/{id}` | 修改自己的已读/星标标记 | 是 | ✅ |
| POST | `/api/v1/mails` | 撰写并直接发送 | 是（限流 Phase 5） | ✅ |
| POST | `/api/v1/drafts` | 新建草稿 | 是 | ✅ |
| PUT | `/api/v1/drafts/{id}` | 更新草稿（整体替换） | 是 | ✅ |
| DELETE | `/api/v1/drafts/{id}` | 删除草稿 | 是 | ✅ |
| POST | `/api/v1/drafts/{id}/send` | 发送草稿 | 是（限流 Phase 5） | ✅ |
| POST | `/api/v1/mails/{id}/recall` | 撤回 | 是 | 📝 |
| GET | `/api/v1/mails/{id}/reply-context` | 回复上下文与快捷回复建议 | 是 | 📝 |
| GET | `/api/v1/mails/search?q=` | 模糊搜索 | 是 | 📝 |
| CRUD | `/api/v1/contact-groups` | 联系人群组 | 是 | 📝 |
| POST | `/api/v1/attachments` | 上传图片附件（multipart） | 是+限流 | 📝 |
| GET | `/api/v1/attachments/{id}` | 下载附件（校验所有权） | 是 | 📝 |

### A.3.1 `GET /api/v1/system/info` ✅

```json
{
  "success": true,
  "data": {
    "domain": "domain-a.test",
    "displayName": "Domain A Corp",
    "apiVersion": "v1",
    "serverVersion": "0.1.0-SNAPSHOT"
  },
  "timestamp": "2026-11-12T06:53:07.789Z"
}
```

### A.3.2 认证接口 ✅

`POST /api/v1/auth/register` → `201`

```json
// 请求
{ "username": "alice", "displayName": "Alice", "password": "Str0ngPassw0rd", "confirmPassword": "Str0ngPassw0rd" }
// 响应 data
{ "username": "alice", "address": "alice@domain-a.test", "displayName": "Alice", "role": "MEMBER" }
```

| 字段 | 规则 |
|---|---|
| `username` | 3–32 位，字母/数字开头和结尾，中间可用 `. _ -`；**不区分大小写，统一存为小写**；不能是保留名（admin、postmaster、security 等） |
| `displayName` | 1–64 字符，不能包含控制字符 |
| `password` | 8–64 字符，至少包含一个字母和一个数字，不能包含用户名 |
| `confirmPassword` | 必须与 `password` 一致（使用常量时间比较） |

注册后**不会自动登录**；新用户一律为 `MEMBER`，加入本域默认组织。请求体中多出的字段（如 `"role": "ADMIN"`）会被拒绝，返回 `400 MALFORMED_REQUEST`。

`POST /api/v1/auth/login` → `200`，响应 data 与注册相同。

```json
{ "username": "alice", "password": "Str0ngPassw0rd" }   // username 也可以写成 "alice@domain-a.test"
```

登录成功后：更换 session ID（防会话固定攻击）→ 轮换 CSRF token → 写入审计日志 `AUTH_LOGIN_SUCCESS`。登录失败统一返回 `401 AUTH_INVALID_CREDENTIALS`，并写入审计日志 `AUTH_LOGIN_FAILURE`。

### A.3.3 邮件接口 ✅

**撰写请求**（`POST /api/v1/drafts`、`PUT /api/v1/drafts/{id}`、`POST /api/v1/mails` 共用同一结构）：

```json
{
  "to":  ["bob@domain-a.test"],
  "cc":  ["carol@domain-a.test"],
  "bcc": ["dave@domain-a.test"],
  "subject": "Q4 plan",
  "body": "纯文本正文"
}
```

| 规则 | 说明 |
|---|---|
| 地址 | 统一转小写；不接受显示名语法、带引号的本地部分、换行等 |
| 去重 | 同一地址出现在多个字段时，保留可见性最高的字段（TO > CC > BCC） |
| 数量 | 去重后最多 50 个收件人 |
| `subject` | ≤ 255 字符，单行 |
| `body` | ≤ 100 000 字符，纯文本 |
| 草稿 | 字段都可以为空；发送时至少需要 1 个收件人 |
| 发送校验 | **全部收件人可投递才发送**，否则整封邮件拒绝、什么都不写入，并在 `details` 中列出每个有问题的地址 |

`MAIL_RECIPIENT_INVALID` 响应示例：

```json
{ "success": false,
  "error": { "code": "MAIL_RECIPIENT_INVALID",
             "message": "One or more recipients cannot receive this mail",
             "details": [ { "field": "ghost@domain-a.test", "reason": "UNKNOWN_RECIPIENT" },
                          { "field": "bob@domain-b.test",   "reason": "DOMAIN_NOT_SUPPORTED" } ] } }
```

> `DOMAIN_NOT_SUPPORTED`：收件人的域名既不是本域，也不在已配置的对端列表里。**已配置对端**的地址会被接受，然后异步投递：发送接口返回 `201` 时，该收件人的状态是 `PENDING`，之后变为 `DELIVERED` 或 `FAILED`（例如对端回复 `RECIPIENT_NOT_FOUND`）。远端用户是否存在只有对端知道，所以无法在发送时同步判断。

**邮件 ID**：所有接口中的 `id` 都是**当前用户自己的邮箱条目 ID**（`mailbox_items.id`），不是全局的邮件 ID。同一封邮件在发件人的"已发送"和收件人的"收件箱"里是不同的 `id`。访问不属于自己的 `id` 一律返回 `404`。

**详情响应 data**（`GET /api/v1/mails/{id}`）：

```json
{
  "id": 42, "folder": "SENT", "messageId": "6f0c…@domain-a.test", "status": "SENT",
  "from": { "address": "alice@domain-a.test", "displayName": "Alice" },
  "to": ["bob@domain-a.test"], "cc": [], "bcc": ["dave@domain-a.test"],
  "subject": "Q4 plan", "body": "…", "read": true, "starred": false,
  "sentAt": "2026-11-12T07:27:55Z", "updatedAt": "2026-11-12T07:27:55Z",
  "delivery": [ { "address": "bob@domain-a.test", "type": "TO", "status": "DELIVERED" } ]
}
```

`bcc` 和 `delivery` **只返回给发件人本人**；其他收件人的响应中没有这两个字段。

`delivery[].status`：`DELIVERED`（已投递）、`PENDING`（跨域，等待投递）、`RETRYING`（跨域，暂时失败，正在重试）、`FAILED`（永久失败）。从对端收到的邮件，收件人列表只用于显示，不跟踪投递状态。

**列表项**（`GET /api/v1/mails?folder=…`）：`id, folder, status, from, to（仅发件人的已发送/草稿）, subject, preview（正文前 120 字符）, read, starred, sentAt, updatedAt`。

**标记**（`PATCH /api/v1/mails/{id}`）：`{"read": true}` 或 `{"starred": true}`，至少提供一个字段；只修改当前用户自己的那一份。

### A.3.4 附件上传/下载约定 📝

- 上传：`multipart/form-data`，字段名 `file`；单文件 ≤ 5 MB，单次请求 ≤ 20 MB（服务端硬上限已配置 ✅）。
- 仅接受 PNG / JPEG / GIF / WebP，以**文件头魔数**判定，扩展名与 `Content-Type` 仅作参考。
- 响应返回服务端生成的不透明 `attachmentId`；客户端无法指定存储路径。
- 下载：`Content-Disposition: attachment`、`X-Content-Type-Options: nosniff`，只对邮件所有者/收件人授权。

---

# Part B：服务器间协议（Federation Protocol v1）

> 状态：投递（B.3）✅ Phase 3 已实现并测试；撤回（B.4）📝 Phase 5；每对端限流 📝 Phase 5。
> 本节为规范，实现须与之一致；如有偏差，先改文档。

## B.1 角色与前提 ✅

- **发送方（Origin）**：发件人所在域的服务器。
- **接收方（Destination）**：收件人所在域的服务器。
- 每个服务器在配置中**静态声明**可信对端（双向都要配置，共享同一个密钥）：

```yaml
ssm:
  federation:
    peers:
      - domain: domain-b.test
        base-url: https://localhost:8082
        key-id: dev-a-b
        # 二选一（必须恰好设置一个）：
        shared-secret: ${...}            # 由环境变量注入，例如 SSM_FEDERATION_PEERS_0_SHARED_SECRET
        shared-secret-file: ./data/federation-dev/domain-a.test__domain-b.test.secret
```

- 启动时校验，配置有误则拒绝启动：密钥至少 32 字节；不能把本域配置为对端；不能重复配置同一个对端；地址必须是 http(s)。
- 密钥不会出现在日志或 `toString()` 中。本地开发用的密钥由 `scripts/init-dev-secrets.sh` 生成（`openssl rand -base64 48`，文件权限 600，不进仓库）。
- **未配置的域名一律拒绝**：发送时同步返回 `DOMAIN_NOT_SUPPORTED`；接收时返回 `401 PEER_UNKNOWN`。

## B.2 传输安全 ✅

| 目标 | 机制 |
|---|---|
| 机密性 | HTTPS（TLS 1.3/1.2）：`tls` profile；发送方只信任配置的 CA（`ssm.federation.ssl-bundle`），不用 JDK 默认信任库。不使用 `tls` 时为 HTTP，启动日志会给出警告 |
| 对端认证 | 每对端共享密钥 + HMAC-SHA256 请求签名（JDK `javax.crypto.Mac`，不自创算法） |
| 完整性 | 签名覆盖方法、路径、来源域、密钥 ID、时间戳、nonce、请求体 SHA-256 |
| 防重放 | 时间戳窗口 ±300 s，nonce 在 900 s 内只能使用一次（`federation_nonces` 表的 `(origin, nonce)` 唯一约束；Phase 5 迁移到 Redis） |
| 幂等 | `messageId` 作为幂等键，重复投递不会重复入库，并返回同样的结果 |
| 请求体上限 | 默认 1 MiB（`ssm.federation.max-envelope-bytes`），超出返回 `413` |
| 通道隔离 | `/federation/**` 使用独立的安全过滤链：无会话、无 CSRF、只认签名；用户会话访问不了联邦端点，签名也访问不了用户 API |

### B.2.1 签名请求头

| 头 | 示例 | 说明 |
|---|---|---|
| `X-SSM-Origin` | `domain-a.test` | 发送方域名 |
| `X-SSM-Key-Id` | `dev-a-b` | 密钥标识（为密钥轮换预留） |
| `X-SSM-Timestamp` | `1762930387` | Unix 秒 |
| `X-SSM-Nonce` | `9b1f0c1e-…` | UUIDv4，每次请求（包括每次重试）都不同；接收方只接受 16–64 位的 `[A-Za-z0-9-]` |
| `X-SSM-Content-SHA256` | `base64(sha256(body))` | 请求体摘要 |
| `X-SSM-Signature` | `base64(hmac)` | 见下 |

签名串（各字段以 `\n` 连接）：

```
SSM-HMAC-SHA256-V1
<HTTP 方法大写>
<请求路径，不含 host 与 query>
<X-SSM-Origin>
<X-SSM-Key-Id>
<X-SSM-Timestamp>
<X-SSM-Nonce>
<X-SSM-Content-SHA256>
```

`X-SSM-Signature = Base64( HMAC-SHA256(shared_secret, 签名串) )`

### B.2.2 接收方校验顺序（`FederationAuthenticationFilter`）

0. 请求体超过上限 → `413 PAYLOAD_TOO_LARGE`
1. 来源域名在对端白名单内，且 `Key-Id` 匹配 → 否则 `401 PEER_UNKNOWN`
2. 时间戳在 ±300 s 内 → 否则 `401 PEER_TIMESTAMP_SKEW`
3. nonce 格式合法，且重新计算的请求体摘要与请求头一致 → 否则 `401 PEER_SIGNATURE_INVALID`
4. 常量时间比较签名（`MessageDigest.isEqual`）→ 否则 `401 PEER_SIGNATURE_INVALID`
5. nonce 未使用过（**放在签名校验之后**，未通过认证的请求无法往 nonce 表里写数据）→ 否则 `409 PEER_REPLAY`
6. 限流（每对端每分钟上限）→ `429 RATE_LIMITED` 📝 Phase 5
7. 业务校验（见各接口）

认证失败都会记入审计日志（事件 `FEDERATION_AUTH_FAILED`，记录来源域名和原因）。

## B.3 投递：`POST /federation/v1/messages` ✅

### 请求（信封）

```json
{
  "protocolVersion": 1,
  "messageId": "6f0c2a8e-4d1b-4c3e-9b0a-2f3d4e5a6b7c@domain-a.test",
  "from": { "address": "alice@domain-a.test", "displayName": "Alice" },
  "recipients": [
    { "address": "bob@domain-b.test",   "type": "TO" },
    { "address": "dave@domain-b.test",  "type": "BCC" }
  ],
  "visibleTo":  ["bob@domain-b.test"],
  "visibleCc":  ["carol@domain-a.test"],
  "subject": "Q4 plan",
  "bodyText": "Hi Bob, ...",
  "inReplyTo": null,
  "sentAt": "2026-11-12T06:53:07Z"
}
```

- `recipients`：要求接收方投递的地址，**只包含该对端域名下仍待投递的收件人**（重试时只发尚未确认的收件人）。
- `visibleTo` / `visibleCc`：所有收件人都能看到的地址（跨所有域），**永远不包含 BCC**。
- 附件字段 `attachments` 📝 Phase 4 加入；当前请求体出现任何未声明的字段都会被拒绝（`400`）。

校验规则：

- 字段校验（Bean Validation）：`protocolVersion` 必须为 1；`recipients` 1–50 个；`subject` ≤ 255 字符且为单行；`bodyText` ≤ 100 000 字符；地址使用严格语法。不合法 → `400 VALIDATION_FAILED`。
- `messageId` 必须是 `<id>@<域名>` 格式，且域名部分等于 `X-SSM-Origin`；`from.address` 的域名也必须等于 `X-SSM-Origin`。否则返回 `400 ORIGIN_MISMATCH`：对端只能代表自己的域发信，**不能伪造第三方域的发件人**。
- 同一个 `messageId` 已存在但来源不同（不是 REMOTE 邮件，或发件人不同）→ `409 CONFLICT`。
- 逐收件人处理：地址不合法 → `INVALID_ADDRESS`；不是本域 → `RECIPIENT_DOMAIN_MISMATCH`；本域没有这个活跃用户 → `RECIPIENT_NOT_FOUND`；否则在该用户的收件箱中创建条目（已存在则跳过）→ `ACCEPTED`。

### 响应（逐收件人回执）

```json
{
  "protocolVersion": 1,
  "messageId": "6f0c2a8e-...@domain-a.test",
  "results": [
    { "address": "bob@domain-b.test",  "status": "ACCEPTED" },
    { "address": "dave@domain-b.test", "status": "REJECTED", "reason": "RECIPIENT_NOT_FOUND" }
  ]
}
```

### 回执语义（发送方 `FederationClient` / `DeliveryDispatcher`）

| 接收方返回 | 含义 | 发送方动作 |
|---|---|---|
| 200 + `ACCEPTED` | 已**持久化**到收件人邮箱（事务提交后才返回响应） | 该收件人 → `DELIVERED` |
| 200 + `REJECTED` | 该收件人永久失败 | 该收件人 → `FAILED`，不重试，记审计 `MAIL_DELIVERY_FAILED` |
| 200 但回执里缺少某个收件人 | 未确认 | 该收件人按暂时失败重试 |
| 200（重复 `messageId`） | 幂等：不重复入库，返回同样的结果 | 同上 |
| 400 / 401 / 403 / 404 / 413 / 415 | 请求本身被拒（配置错误或内容不合规） | 这批收件人全部 `FAILED`，不重试 |
| 409 / 429 / 5xx / 超时 / 连接失败 / 回执无法解析 | 暂时失败（409 可能是并发投递引起的唯一约束冲突） | `RETRYING`，按退避重试 |

**重试策略**：退避间隔可配置（`ssm.federation.retry-delays`，默认 `30s, 1m, 2m, 5m, 15m, 30m`）。总尝试次数 = 间隔数 + 1，用完后标记为 `FAILED`（审计记录"Gave up after N attempts"）。每次重试都用**新的 nonce 和时间戳**，`messageId` 保持不变。

**可靠性**：发送事务内同时写入发件队列（`delivery_outbox`，transactional outbox）。后台投递器每 `dispatch-interval-ms`（默认 1 s）轮询一次，每次投递分三步：短事务构造信封 → 在事务外发 HTTP 请求 → 短事务应用回执。如果在发出请求之后、应用回执之前进程崩溃，这条记录仍然是 `PENDING`，下次会重发；接收方按 `messageId` 幂等，所以不会重复投递。

## B.4 撤回：`POST /federation/v1/recalls` 📝 Phase 5

```json
{
  "protocolVersion": 1,
  "recallId": "c1d2e3f4-...@domain-a.test",
  "messageId": "6f0c2a8e-...@domain-a.test",
  "requestedBy": "alice@domain-a.test",
  "recipients": ["bob@domain-b.test"],
  "requestedAt": "2026-11-12T07:10:00Z"
}
```

接收方校验：签名有效；`messageId` 与 `requestedBy` 的域名都等于 `X-SSM-Origin`；本库中该 `messageId` 存在，且其 `sender_address == requestedBy`（只有原发件人能撤回）。

响应：

```json
{
  "recallId": "c1d2e3f4-...@domain-a.test",
  "results": [ { "address": "bob@domain-b.test", "status": "RECALLED", "wasRead": true } ]
}
```

`status ∈ { RECALLED, NOT_FOUND, REJECTED }`。重复 `recallId` 返回相同结果（幂等）。发送方**只有收到 `RECALLED`** 才把该收件人标记为 `RECALLED`；其他情况标记 `RECALL_FAILED` 并如实展示给用户。

## B.5 服务器间错误码

| code | HTTP | 含义 | 状态 |
|---|---|---|---|
| `PEER_UNKNOWN` | 401 | 未配置的对端或密钥 ID | ✅ |
| `PEER_TIMESTAMP_SKEW` | 401 | 时间戳超出窗口 | ✅ |
| `PEER_SIGNATURE_INVALID` | 401 | 签名或摘要不匹配、缺少签名头、nonce 格式不合法 | ✅ |
| `PEER_REPLAY` | 409 | nonce 已使用过 | ✅ |
| `PAYLOAD_TOO_LARGE` | 413 | 请求体超过上限 | ✅ |
| `VALIDATION_FAILED` | 400 | 信封字段校验失败（包括不支持的 `protocolVersion`） | ✅ |
| `MALFORMED_REQUEST` | 400 | JSON 无法解析或含未知字段 | ✅ |
| `ORIGIN_MISMATCH` | 400 | `messageId` / `from` 的域名与已认证的 Origin 不一致 | ✅ |
| `CONFLICT` | 409 | `messageId` 已被另一封邮件占用，或并发冲突 | ✅ |
| `RATE_LIMITED` | 429 | 对端请求过频 | 📝 Phase 5 |

## B.6 版本演进

`protocolVersion` 用于向后兼容；接收方遇到不支持的版本返回 `400 VALIDATION_FAILED`。签名中包含 `Key-Id`，为密钥轮换预留（当前每个对端只配置一个密钥；同时接受新旧两个密钥 📝）。
