# 紫云博客 · 部署方案（Nginx + Spring Boot + MySQL + Redis）

> 本文档说明如何把本项目部署到一台 Linux 服务器。
> 架构：Nginx 托管前端静态文件并反向代理 `/api` 到后端；后端以 fat jar 运行。

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
