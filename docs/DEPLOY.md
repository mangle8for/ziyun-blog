# 紫云博客 · 部署方案（Nginx + Spring Boot + MySQL + Redis）

> 本文档说明如何把本项目部署到一台 Linux 服务器。
> **方案 A（推荐，第 9~12 章）：Docker Compose 全容器化 + GitHub Actions 自动部署。**
> 方案 B（第 1~8 章）：Nginx 托管前端静态文件并反向代理 `/api` 到后端；后端以 fat jar 裸机运行（备选，已实测）。

## 1. 部署架构

```
浏览器
  │  https://your-domain.com
  ▼
Nginx（80/443）
  ├── /            → 前端静态文件（blog-web/dist，SPA fallback）
  ├── /assets/*    → 静态资源（带 hash，可长缓存）
  └── /api/*       → 反向代理 http://127.0.0.1:8080（后端）
                          │
                          ├── MySQL 8（ziyun_blog 库）
                          └── Redis 7（登录态/限流）
```

## 2. 服务器前置依赖

| 组件 | 版本要求 | 用途 |
| --- | --- | --- |
| JDK | 17+（建议 21） | 运行后端 jar |
| MySQL | 8.x | 业务数据 |
| Redis | 6.x+ | 登录态、登录限流 |
| Node.js | 22+（仅构建机需要） | 构建前端 |
| Nginx | 1.20+ | 静态托管 + 反向代理 |

## 3. 数据库初始化

```bash
mysql -uroot -p < blog-server/sql/init.sql
```

`init.sql` 会创建 `ziyun_blog` 库、表结构，并预置管理员账号（默认 `admin/admin123`，**上线后必须立即修改密码**）。

## 4. 构建

### 4.1 构建前端

```bash
cd blog-web
npm ci            # 按 lockfile 安装依赖（比 npm install 更可复现）
npm run build     # 产出 dist/ 目录（含 type-check）
```

### 4.2 打包后端

```bash
cd blog-server
./mvnw clean package -DskipTests   # 产出 target/blog-server-0.0.1-SNAPSHOT.jar
```

## 5. 后端部署

### 5.1 环境变量（必须全部就绪，缺一个启动失败）

```bash
# 数据库（useSSL=true，若内网无证书可改为 false）
export DB_HOST=127.0.0.1
export DB_PORT=3306
export DB_NAME=ziyun_blog
export DB_USERNAME=blog
export DB_PASSWORD='<强密码>'

# Redis
export REDIS_HOST=127.0.0.1
export REDIS_PORT=6379

# JWT 签名密钥（>=32 字节随机串，生成：openssl rand -base64 48）
export JWT_SECRET='<随机生成>'

# 阿里云 OSS（若 STORAGE_TYPE=oss）
export OSS_ENDPOINT='https://oss-cn-beijing.aliyuncs.com'
export OSS_ACCESS_KEY_ID='<RAM 子账号 AK>'
export OSS_ACCESS_KEY_SECRET='<RAM 子账号 SK>'
export OSS_BUCKET='<bucket 名>'

# 可选
# export SERVER_PORT=8080
# export STORAGE_TYPE=oss
# 跨域白名单（同域部署无需设置）：export CORS_ALLOWED_ORIGINS='https://blog.example.com'
```

> 安全提醒：OSS AccessKey 务必使用 RAM 子账号，仅授予本 bucket 的
> `oss:PutObject` / `oss:DeleteObject` 权限，不要用主账号 AK。

### 5.2 启动 / 停止

```bash
# 前台启动（验证用）
java -jar blog-server-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod

# 后台运行
nohup java -jar blog-server-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=prod \
  > /dev/null 2>&1 &

# 停止
kill $(pgrep -f blog-server-0.0.1-SNAPSHOT.jar)
```

日志输出到 `logs/blog-server.log`（按天滚动，保留 14 天，单文件 100MB）。

### 5.3 建议：systemd 托管（开机自启 + 崩溃重启）

`/etc/systemd/system/blog-server.service`：

```ini
[Unit]
Description=Ziyun Blog Server
After=network.target mysql.service redis-server.service

[Service]
User=blog
WorkingDirectory=/opt/blog-server
EnvironmentFile=/opt/blog-server/env.conf        # 环境变量集中放此文件
ExecStart=/usr/bin/java -jar /opt/blog-server/blog-server.jar --spring.profiles.active=prod
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now blog-server
sudo systemctl status blog-server
```

## 6. Nginx 部署

### 6.1 放置前端产物

```bash
mkdir -p /opt/blog-web
cp -r blog-web/dist/* /opt/blog-web/
```

### 6.2 站点配置

`/etc/nginx/conf.d/blog.conf`：

```nginx
server {
    listen 80;
    server_name your-domain.com;          # 换成你的域名
    client_max_body_size 6m;              # 与后端 multipart 限制对齐（封面上传）

    # 前端静态资源：带 hash 的文件可长缓存，index.html 不缓存（防更新后拿到旧壳）
    root /opt/blog-web;

    location /assets/ {
        expires 30d;
        add_header Cache-Control "public, immutable";
    }

    # SPA history 路由回退：未知路径一律回 index.html，由前端路由接管
    location / {
        try_files $uri $uri/ /index.html;
    }

    # API 反向代理到后端
    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 60s;
    }
}
```

### 6.3 启用 + 验证

```bash
sudo nginx -t                       # 配置语法检查
sudo systemctl reload nginx         # 平滑重载
curl -I http://your-domain.com/     # 应返回 200 + index.html
curl -I http://your-domain.com/api/v1/categories   # 应返回 JSON 列表
```

### 6.4 （可选）HTTPS：certbot 一键签发

```bash
sudo apt install certbot python3-certbot-nginx
sudo certbot --nginx -d your-domain.com
```

certbot 会自动改写站点配置并配置续期定时任务。

## 7. 上线检查清单

- [ ] `admin` 初始密码已修改（或直接改 init.sql 预置密码后重新建库）
- [ ] `JWT_SECRET` 已用随机串生成，未使用默认值
- [ ] OSS AccessKey 为 RAM 子账号、仅授予本 bucket 权限
- [ ] MySQL 使用独立账号（非 root）、强密码，仅授权 `ziyun_blog.*`
- [ ] `--spring.profiles.active=prod` 已生效（生产 SQL 日志已关闭）
- [ ] Nginx `nginx -t` 通过，`/api` 反代验证 200
- [ ] 文章上传封面功能实测可用（验证 OSS 上传链路）
- [ ] 登录 → 写文章 → 发布 全流程走通
- [ ] 日志落盘正常（`logs/blog-server.log` 存在且有内容）

## 8. 回滚方案

前端是静态文件，回滚 = 覆盖 `/opt/blog-web` 下文件（旧 dist 留备份）：

```bash
mv /opt/blog-web /opt/blog-web.bak.$(date +%Y%m%d%H%M)
cp -r old-dist /opt/blog-web
```

后端回滚 = 保留旧 jar 副本，替换后重启：

```bash
cp blog-server.jar blog-server.jar.bak.$(date +%Y%m%d%H%M)
sudo systemctl restart blog-server
```

---

# 方案 A：Docker Compose + GitHub Actions 自动部署（推荐）

> 目标环境：Ubuntu 22.04，2C2G。push 到 GitHub main 分支即自动构建并部署。
> 仓库内文件：`.github/workflows/deploy.yml`（CI/CD）+ `deploy/`（编排/配置/脚本）。

## 9. 部署架构与内存预算

```
GitHub Actions（免费 runner，构建不占服务器资源）
  ├─ Node 22：npm ci && npm run build → dist/
  ├─ JDK 17：./mvnw package -DskipTests → fat jar
  └─ rsync + ssh → 服务器（产物 + .env）
                            ▼
服务器 2C2G（Docker Compose 编排，全部容器 mem_limit 上限约 1.92G）
  nginx:1.27-alpine   128m    静态托管 dist + 反代 /api → backend:8080
  backend（temurin17-jre）1024m  -Xms256m -Xmx512m，env 注入生产密钥
  mysql:8.0           512m     调优后实际约 250MB，数据卷持久化
  redis:7-alpine      256m     maxmemory 128mb，关闭持久化（登录态可重建）
```

> 内存说明：Docker 本身不省内存（进程占用与裸机相同，还多约 100MB daemon
> 开销），真正的空间来自调优 —— JVM 堆显式定死、MySQL 缓冲池 64M + 关
> performance_schema、Redis maxmemory 限制。建议服务器额外配 2G swap。

## 10. 服务器一次性准备（Ubuntu 22.04）

```bash
# 0) 换 apt 镜像源（大陆服务器必做，默认源访问 archive.ubuntu.com 很慢）
cp /etc/apt/sources.list /etc/apt/sources.list.bak
cat > /etc/apt/sources.list <<'EOF'
deb https://mirrors.aliyun.com/ubuntu/ jammy main restricted universe multiverse
deb https://mirrors.aliyun.com/ubuntu/ jammy-updates main restricted universe multiverse
deb https://mirrors.aliyun.com/ubuntu/ jammy-backports main restricted universe multiverse
deb https://mirrors.aliyun.com/ubuntu/ jammy-security main restricted universe multiverse
EOF
apt update

# 1) 基础包 + rsync（部署传输依赖）
apt install -y curl vim git rsync

# 2) 安装 Docker + Compose 插件（官方源）
curl -fsSL https://get.docker.com | sh
systemctl enable --now docker

# 2.1) Docker 镜像加速（否则 pull nginx/mysql 等镜像极慢；阿里云专属地址
#     在 容器镜像服务控制台 -> 镜像加速器 页领取，格式 https://<你的>.mirror.aliyuncs.com）
cat > /etc/docker/daemon.json <<'EOF'
{
  "registry-mirrors": [
    "https://<你的专属地址>.mirror.aliyuncs.com",
    "https://docker.m.daocloud.io"
  ]
}
EOF
systemctl restart docker

# 3) 2G swap（2G 内存机器的保险丝）
fallocate -l 2G /swapfile && chmod 600 /swapfile
mkswap /swapfile && swapon /swapfile
echo '/swapfile none swap sw 0 0' >> /etc/fstab

# 4) 放行端口（SSH/HTTP/HTTPS）
ufw allow 22/tcp && ufw allow 80/tcp && ufw allow 443/tcp && ufw enable

# 5) 部署目录
mkdir -p /opt/ziyun-blog/{backend,nginx,mysql,redis,logs,scripts,releases}

# 6) 生成部署专用密钥对（私钥将配到 GitHub Secret）
ssh-keygen -t ed25519 -C "github-actions" -f ~/.ssh/github_actions -N ""
cat ~/.ssh/github_actions.pub >> ~/.ssh/authorized_keys
chmod 600 ~/.ssh/authorized_keys
cat ~/.ssh/github_actions        # 复制私钥全文，配到 SERVER_SSH_KEY
```

DNS：把 `ziyun.fun`（及 www）的 A 记录指向服务器公网 IP。

## 11. GitHub 侧配置

### 11.1 推送仓库

```bash
git remote add origin git@github.com:mangle8for/ziyun-blog.git
git push -u origin main
```

### 11.2 添加 Secrets

仓库 Settings → Secrets and variables → Actions → New repository secret：

| Secret | 值 |
| --- | --- |
| `SERVER_HOST` | 服务器公网 IP |
| `SERVER_PORT` | 22 |
| `SERVER_USER` | root |
| `SERVER_SSH_KEY` | 上一步生成的私钥全文（含 BEGIN/END 行） |
| `DOMAIN` | ziyun.fun |
| `MYSQL_ROOT_PASSWORD` | 部署脚本生成的强密码 |
| `DB_PASSWORD` | 部署脚本生成的强密码 |
| `JWT_SECRET` | 部署脚本生成的随机串（>= 32 字节） |
| `OSS_ACCESS_KEY_ID` | RAM 子账号 AK（**必填**，仅授予本 bucket 上传/删除权限） |
| `OSS_ACCESS_KEY_SECRET` | RAM 子账号 SK（**必填**） |

> ⚠️ 对象存储凭据**必填**：`deploy/docker-compose.yml` 已改为
> `${OSS_ACCESS_KEY_ID:?}` / `${OSS_ACCESS_KEY_SECRET:?}` 必填语法，
> 缺失时 compose 直接报错退出（防历史「默认复用开发凭据」的漏洞复现）。
> 历史硬编码的开发 AK/SK 曾随公开仓库泄露——**务必已在阿里云控制台轮换**，
> 并把新凭据填入上述 Secrets；部署时 Actions 自动注入服务器 `.env`。
>
> 本地开发（dev profile）：JWT 与 OSS 凭据默认值见 `application-dev.yml`，
> 本地联调可在 IDE 环境变量中注入（JWT_SECRET 已提供 dev 默认值；
> OSS 凭据未配置时仅上传图片接口不可用）。

## 12. 首次部署与验证

1. 推送 main → Actions 自动构建并部署（首次约 3~5 分钟，MySQL 数据卷为空时自动执行 `init.sql` 建库建表）
2. 验证：
   ```bash
   curl -I http://ziyun.fun/                          # 前端 200
   curl http://ziyun.fun/api/v1/categories            # 反代 + 后端 + 数据库 JSON
   docker compose -f /opt/ziyun-blog/docker-compose.yml ps   # 全部 healthy
   ```
3. 浏览器登录（admin / admin123）→ **立即修改密码** → 实测写文章/传封面

### 回滚

- 部署失败：deploy.sh 自动回滚到上一版（`dist.prev` / `app.jar.prev`）并退出码 1
- 手动回滚：历史版本在 `/opt/ziyun-blog/releases/<sha>/`，复制其中 dist/app.jar 到当前位并 `docker compose up -d --build`
- 查看日志：`docker logs -f ziyun-backend`；应用日志落盘 `/opt/ziyun-blog/logs/blog-server.log`

### HTTPS（Let's Encrypt，推荐）

免费正式证书（浏览器无警告）+ 自动续期。仓库配置已启用 443（blog.conf 双 server 块 +
compose 443 端口 + /etc/letsencrypt 挂载），证书签发后部署即可生效。

#### 13.1 签发证书（服务器一次性）

```bash
# 1) 安装 certbot（自带 systemd 定时器，自动续期）
apt install -y certbot

# 2) 签发：standalone 模式临时占用 80 端口，pre/post hook 自动停启 nginx
#    （hooks 会被写进 renewal 配置，未来自动续期同样生效，无需干预）
docker compose -f /opt/ziyun-blog/docker-compose.yml stop nginx   # 可选，pre-hook 已处理
certbot certonly --standalone -d ziyun.fun \
  --register-unsafely-without-email --agree-tos -n \
  --pre-hook  "docker compose -f /opt/ziyun-blog/docker-compose.yml stop nginx" \
  --post-hook "docker compose -f /opt/ziyun-blog/docker-compose.yml start nginx"

# 3) 验证证书已生成
ls /etc/letsencrypt/live/ziyun.fun/
```

> www.ziyun.fun 若已解析到本机，可在签发命令追加 `-d www.ziyun.fun`。

#### 13.2 启用 HTTPS（推送部署）

```bash
git add deploy/nginx/blog.conf deploy/docker-compose.yml
git commit -m "feat: 启用 HTTPS(Let's Encrypt)"
git push
```

部署脚本会重建 nginx 容器加载 443 配置。**顺序不可反**：先签发证书再推送
（nginx 启动时证书文件必须存在，否则容器起不来会触发回滚）。

#### 13.3 验证与续期

```bash
curl -sI https://ziyun.fun/ | head -3          # 200 + ssl 正常
certbot certificates                            # 查看证书到期时间
systemctl list-timers | grep certbot            # 自动续期定时器（每天检查两次）
```

- 续期：certbot 自动执行（到期前 30 天起续期），hooks 自动停/启 nginx，全程无需干预
- 手动续期测试：`certbot renew --dry-run`
- 证书失效场景：80 端口被占用（hooks 未执行成功）时续期失败，需检查容器状态
