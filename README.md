# Smart Secure Mail

企业级智能安全邮箱：一份服务端代码部署为两个相互隔离的域名（`domain-a.test` / `domain-b.test`），两者通过自定义的签名协议互相投递邮件。

- 架构：[docs/architecture.md](docs/architecture.md)
- 协议：[docs/protocol.md](docs/protocol.md)
- 安全与威胁模型：[docs/security.md](docs/security.md)
- 题目要求：[docs/requirements.md](docs/requirements.md)
- 当前进度：[HANDOFF.md](HANDOFF.md)

## 快速开始（本地，无需 Docker）

需要 JDK 21。

```bash
scripts/run-local.sh domain-a    # 终端 1 → http://localhost:8081
scripts/run-local.sh domain-b    # 终端 2 → http://localhost:8082

curl -s localhost:8081/api/v1/system/info
curl -s localhost:8082/api/v1/system/info
```

两个实例互为对端，可以互相发信（`alice@domain-a.test` ↔ `bob@domain-b.test`）。首次运行会自动生成本地联邦密钥（`server/data/federation-dev/`，不进仓库）。

HTTPS 模式：

```bash
scripts/gen-dev-certs.sh            # 生成开发 CA 和证书（只需一次）
scripts/run-local.sh domain-a tls   # https://localhost:8081
scripts/run-local.sh domain-b tls   # https://localhost:8082
```

## 客户端（React）

需要 Node 20.19+ / 22.12+。先启动两个后端，然后：

```bash
cd client && npm install
npm run dev:a    # http://localhost:5173 → domain-a.test
npm run dev:b    # http://localhost:5174 → domain-b.test
```

在两个浏览器窗口里分别注册 domain-a 和 domain-b 的用户，就可以互相发信，并在"已发送"里看到逐个收件人的投递回执。

## 测试

```bash
cd server && ./mvnw test          # 单元 + 集成 + 真实 HTTP 测试
scripts/e2e-local-mail.sh         # 对运行中的两个实例做端到端检查（含跨域互发）
cd client && npm test             # 前端单元测试（Vitest）
cd client && npm run e2e          # 浏览器端到端测试（Playwright，使用本机 Chrome）
```

## Docker（PostgreSQL）

```bash
cp deploy/.env.example deploy/.env   # 替换所有密码和 FEDERATION_SECRET_A_B
docker compose -f deploy/docker-compose.yml --env-file deploy/.env up --build
```
