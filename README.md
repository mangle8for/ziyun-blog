# 紫云博客 · 星海拾遗

从零构建的前后端分离个人博客系统：前台是以「星海」为主题的沉浸式阅读站，后台覆盖文章 / 分类 / 标签 / 用户管理与 AI 写作助手。

生产域名：<https://ziyun.fun>（Nginx + Let's Encrypt 提供 HTTPS）

| 层次 | 技术选型 |
| --- | --- |
| 前端 | Vue 3.5 · TypeScript · Vite 8 · Vue Router · Pinia · Element Plus（按需引入） · Tiptap 3 · @unhead/vue |
| 后端 | Spring Boot 3.5（JDK 17） · MyBatis-Plus 3.5 · Spring Security + JWT · Spring Data Redis |
| 存储 | MySQL 8（utf8mb4） · Redis 7 · 阿里云 OSS / MinIO（存储抽象层，切换只改配置） |
| 部署 | Nginx · Docker Compose · GitHub Actions · Let's Encrypt |

## 目录

- [架构总览](#架构总览)
- [功能一览](#功能一览)
- [目录结构](#目录结构)
- [快速开始](#快速开始)
- [配置说明](#配置说明)
- [接口一览](#接口一览)
- [数据库设计](#数据库设计)
- [部署](#部署)
- [相关文档](#相关文档)
- [安全约定](#安全约定)

## 架构总览

```text
浏览器
  │
  ├── 静态资源（HTML / JS / CSS）───► Nginx ─────► 前端构建产物 dist/
  │
  └── /api/** · /sitemap.xml ──────► Nginx 反向代理 ──► Spring Boot :8080
                                                          ├── MySQL 8    业务数据
                                                          ├── Redis 7    登录态 / 限流
                                                          └── 对象存储    图片 / 头像
```

前后端完全分离：前端是纯静态 SPA，生产环境由 Nginx 托管静态资源并把 `/api` 反代到后端 8080；后端只提供 JSON 接口，不渲染任何页面。

## 功能一览

### 前台

- **首页**：全视口星海首屏（逐字标题、流星、星尘萤火、光带扫光），置顶文章星耀推荐区，「设计思路」栏目滚动联动（scrollytelling），分类 / 标签热门 TopN 折叠
- **文章详情**：正文渲染与代码高亮、目录（TOC）定位、上一篇 / 下一篇导航、浏览量统计
- **归档**：时间轴 + 卡片流，下滑无限加载
- **搜索**：标题 / 分类 / 标签 / 时间组合检索
- **分类与标签**：分类页「星域档案」、标签页「散落星海」两套独立布局
- **关于**：博主卡片、技能栈可视化、站点实时统计
- **主题**：暗色「星际拓荒」/ 亮色「自然绿意」双主题，持久化到 localStorage 并防首屏闪烁
- **加载体验**：路由懒加载、骨架屏、图片渐进式加载（低清预览 → 高清）

### 管理端（需管理员）

- **文章管理**：列表筛选、发布 / 撤回草稿、置顶、删除；Tiptap 所见即所得编辑器，支持表格 / 代码块 / 高亮 / 图片上传
- **分类管理**、**标签管理**：完整增删改查
- **用户管理**：新增用户、启用 / 停用、重置密码、删除
- **AI 供应商设置**：多供应商（OpenAI 兼容协议）、API Key 加密落库、连通性测试、设为写作默认模型

### 个人中心（登录用户）

资料编辑、修改密码、头像上传（每月 3 次限流）

### AI 写作助手

基于 SSE 的流式代理，打字机式输出，支持 **润色 / 续写 / 摘要 / 生成标题 / 提取标签 / 自定义指令**；续写提供风格预设与自定义写作要求；长文自动接续、心跳保活，并对网络中断做了容错。

### 安全与运维

- JWT 无状态认证（72 小时）+ Redis 登录态；登录失败限流锁定，前端展示实时剩余时间
- 不开放公开注册，账号由管理员统一创建
- 上传校验：5MB 上限 + 扩展名白名单 + 文件魔数校验
- 统一响应结构与全局异常处理，参数校验（JSR-380）
- 数据层：雪花 ID、逻辑删除、不建物理外键
- 生产密钥全部经环境变量注入，必填项缺失即启动失败；生产环境关闭 SQL 日志
- Nginx 安全头：CSP、HSTS、X-Frame-Options、X-Content-Type-Options、Referrer-Policy

## 目录结构

```text
zi-yun-blog/
├── blog-web/                     # 前端：Vue 3 + Vite SPA
│   ├── src/
│   │   ├── api/                  # 接口封装（axios，按模块拆分）
│   │   ├── components/           # 通用组件：星空背景 / 毛玻璃卡片 / 渐进式图片 / Tiptap 编辑器…
│   │   ├── config/               # 站点静态配置（关于页文案与技能栈）
│   │   ├── layouts/              # PublicLayout（前台）/ AdminLayout（后台）
│   │   ├── router/               # 路由与登录、管理员守卫
│   │   ├── stores/               # Pinia：用户登录态
│   │   ├── utils/                # axios 实例 / 主题切换
│   │   └── views/                # 页面（admin/ 为管理端）
│   ├── scripts/                  # 迁移脚本（Markdown 正文转 HTML）
│   └── public/                   # robots.txt / favicon
├── blog-server/                  # 后端：Spring Boot REST API
│   ├── src/main/java/fun/ziyun/blogserver/
│   │   ├── common/               # 统一响应 Result / 分页 PageResult / 响应码枚举
│   │   ├── config/               # Security、MyBatis-Plus、Redis、Jackson、异步、OSS/AI 配置绑定
│   │   ├── controller/           # 接口层
│   │   ├── dto/ · vo/            # 入参校验对象 / 出参视图对象
│   │   ├── entity/ · mapper/     # 实体与 MyBatis-Plus Mapper
│   │   ├── filter/ · security/   # JWT 过滤器与 UserDetails 实现
│   │   ├── service/              # 业务层（含对象存储实现、AI 流式代理）
│   │   └── util/                 # JWT / AI Key 加解密
│   ├── sql/
│   │   ├── init.sql              # 建库建表 + 示例数据 + 预置管理员
│   │   └── upgrade/              # 增量迁移脚本（按日期命名）
│   └── src/main/resources/       # application.yml / -dev / -prod / logback-spring.xml
├── deploy/                       # 部署编排
│   ├── docker-compose.yml        # nginx + backend + mysql + redis
│   ├── nginx/blog.conf           # 站点配置（静态托管 / 反向代理 / 安全头 / HTTPS）
│   ├── backend/Dockerfile        # 后端镜像构建
│   ├── mysql/ · redis/           # 小内存调优配置
│   ├── scripts/deploy.sh         # 发布脚本（健康检查 + 失败自动回滚）
│   └── .env.example              # 生产环境变量模板
├── docs/
│   ├── DEPLOY.md                 # 完整部署文档
│   └── SEO.md                    # SEO 实施与站长平台提交
└── .github/workflows/deploy.yml  # CI/CD：推送 main 自动构建并发布
```

## 快速开始

### 环境要求

| 依赖 | 版本 |
| --- | --- |
| JDK | 17+（项目目标版本为 17） |
| Maven | 无需安装，使用仓库自带 `mvnw` |
| Node.js | `^22.18.0` 或 `>=24.12.0` |
| MySQL | 8.0+ |
| Redis | 7+（登录态与限流使用） |

### 1. 初始化数据库

```bash
mysql -uroot -p < blog-server/sql/init.sql
```

脚本会创建库 `ziyun_blog`、建表并写入示例数据，同时预置一个管理员账号（**首次登录后请立即修改密码**）。

已有数据库升级时，按顺序执行 `blog-server/sql/upgrade/` 下按日期命名的增量脚本。

### 2. 启动后端

```bash
cd blog-server

# 本地开发密钥与对象存储凭据：
# 未配置 OSS 凭据时仅「图片上传」不可用，其余功能正常
export JWT_SECRET="本地开发用的 32 字节以上随机串"
export OSS_ACCESS_KEY_ID="..."
export OSS_ACCESS_KEY_SECRET="..."

./mvnw spring-boot:run
```

默认激活 `dev` 环境，接口地址 <http://localhost:8080>，该环境会打印 SQL 日志便于调试。

### 3. 启动前端

```bash
cd blog-web
npm install
npm run dev
```

访问 <http://localhost:5173>。开发服务器会把 `/api` 代理到后端 8080（见 `vite.config.ts`），因此无需额外配置跨域。

### 常用命令

```bash
# 前端
cd blog-web
npm run build     # 类型检查 + 构建产物到 dist/
npm run lint      # ESLint 检查并自动修复
npm run format    # Prettier 格式化

# 后端
cd blog-server
./mvnw clean package        # 打包 fat jar 到 target/
./mvnw test                 # 运行测试
```

## 配置说明

后端配置分三层：`application.yml`（公共）+ `application-dev.yml` / `application-prod.yml`（环境覆盖），同名 key 由环境文件覆盖公共值。敏感项一律通过环境变量注入，仓库中不含任何真实凭据。

| 环境变量 | 说明 | 本地开发 | 生产 |
| --- | --- | --- | --- |
| `DB_PASSWORD` | MySQL 密码 | 默认 `root` | **必填** |
| `JWT_SECRET` | JWT 签名密钥（≥32 字节随机串） | dev 有默认值 | **必填，缺失启动失败** |
| `OSS_ACCESS_KEY_ID` / `OSS_ACCESS_KEY_SECRET` | 对象存储凭据（建议 RAM 子账号） | 可空（上传不可用） | **必填** |
| `OSS_BUCKET` / `OSS_BASE_PATH` | 桶名 / 桶内目录前缀 | 默认 `ziyun-webstudy-project` / `blog` | 默认同左 |
| `OSS_ENDPOINT` | 对象存储 Endpoint | 阿里云北京节点 | 同左 |
| `STORAGE_TYPE` | 存储实现：`oss` / `minio` | `oss` | `oss` |
| `AI_ENCRYPT_KEY` | AI Key 落库加密密钥 | 可空（回退 `JWT_SECRET`） | 建议显式配置 |
| `SITE_URL` | 站点根地址（生成 sitemap 用） | 默认 `https://ziyun.fun` | 同左 |
| `CORS_ALLOWED_ORIGINS` | 跨域白名单，逗号分隔 | 同域部署留空 | 同域部署留空 |
| `SERVER_PORT` | 后端监听端口 | `8080` | `8080` |
| `DB_HOST` / `DB_PORT` / `DB_NAME` | 数据库连接 | `localhost` / `3306` / `ziyun_blog` | compose 网络内服务名 |
| `DB_USERNAME` | 数据库账号 | `root` | 业务账号 `blog` |
| `REDIS_HOST` / `REDIS_PORT` | Redis 连接 | `localhost` / `6379` | `redis` / `6379` |

前端仅一个变量 `VITE_API_BASE_URL`：开发与生产都留空，接口 URL 使用全路径 `/api/v1/...`，由 Vite 代理或 Nginx 反代转发。若将来 API 部署到独立域名，在此填入完整地址即可，前后端代码零改动。

## 接口一览

统一前缀 `/api/v1`，响应体为 `Result<T>`（`code` / `msg` / `data`），分页数据为 `PageResult<T>`。

**权限规则**：登录接口与所有 GET 查询公开；`/users/**`、`/ai/**` 需管理员；写操作除个人资料相关外均需管理员。

### 认证 `/api/v1/auth`

| 方法 | 路径 | 说明 | 权限 |
| --- | --- | --- | --- |
| POST | `/login` | 登录并签发 JWT | 公开 |
| POST | `/logout` | 登出（清理 Redis 登录态） | 登录 |
| GET | `/me` | 当前登录用户信息 | 登录 |
| PUT | `/profile` | 修改个人资料 | 登录 |
| PUT | `/password` | 修改密码 | 登录 |
| POST | `/avatar` | 上传头像 | 登录 |

### 文章 `/api/v1/articles`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/` | 公开分页列表（支持分类 / 标签 / 关键字筛选） |
| GET | `/pinned` | 置顶文章 |
| GET | `/{id}` | 文章详情（含上一篇 / 下一篇） |
| GET | `/manage` | 管理端列表（含草稿） |
| GET | `/manage/{id}` | 管理端详情 |
| POST | `/` | 新建文章 |
| PUT | `/{id}` | 更新文章 |
| PUT | `/{id}/status` | 发布 / 撤回草稿 |
| PUT | `/{id}/pinned` | 置顶 / 取消置顶 |
| DELETE | `/{id}` | 删除文章 |

### 分类 `/api/v1/categories` · 标签 `/api/v1/tags`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/` | 列表 |
| GET | `/hot` | 热门 TopN（前台首页用） |
| POST | `/` | 新增 |
| PUT | `/{id}` | 修改 |
| DELETE | `/{id}` | 删除 |

### 用户 `/api/v1/users`（管理员）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/` | 用户分页列表 |
| POST | `/` | 新增用户 |
| PUT | `/{id}/status` | 启用 / 停用 |
| PUT | `/{id}/password/reset` | 重置密码 |
| DELETE | `/{id}` | 删除用户 |

### 文件与 AI

| 方法 | 路径 | 说明 | 权限 |
| --- | --- | --- | --- |
| POST | `/api/v1/files` | 上传图片（返回访问 URL） | 管理员 |
| POST | `/api/v1/ai/chat/stream` | AI 写作对话（SSE 流式） | 管理员 |
| GET / POST / PUT / DELETE | `/api/v1/ai/providers`（`/{id}` 为单条操作） | 模型供应商增删改查 | 管理员 |
| PUT | `/api/v1/ai/providers/{id}/default` | 设为写作默认模型 | 管理员 |
| POST | `/api/v1/ai/providers/{id}/test` | 供应商连通性测试 | 管理员 |

### SEO

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/v1/sitemap.xml` | 动态站点地图（nginx 根路径 `/sitemap.xml` 已反代到此接口） |

## 数据库设计

库名 `ziyun_blog`，字符集 `utf8mb4` / `utf8mb4_unicode_ci`。

| 表 | 说明 |
| --- | --- |
| `user` | 用户（`role`：0 普通用户 / 1 管理员；`status`：0 禁用 / 1 正常；密码存 BCrypt 哈希） |
| `category` | 分类 |
| `tag` | 标签 |
| `article` | 文章（`status`：0 草稿 / 1 已发布；`pinned` 置顶；`view_count` 异步累加；`like_count` 预留） |
| `article_tag` | 文章与标签的多对多关联 |
| `ai_provider` | AI 供应商配置（`api_key` 为 AES/GCM 加密后的密文） |

全局约定：

- **主键非自增**，统一 `BIGINT` + 雪花 ID，由 MyBatis-Plus `assign_id` 策略在应用层生成 —— 避免自增 ID 被遍历抓取，也便于分布式扩展
- **逻辑删除**：所有表带 `deleted` 字段（0 未删 / 1 已删），MyBatis-Plus 自动为查询追加 `AND deleted = 0`
- **不建物理外键**：分类、标签、作者的关联由业务代码保证一致性，避免写操作顺序约束与分库分表失效问题
- **索引策略**：文章表按 `status` / `category_id` / `author_id` 建单列索引，匹配列表页「按状态 + 分类过滤 + 时间倒序」的查询模式

## 部署

推荐使用 Docker Compose + GitHub Actions 自动发布：

1. 服务器一次性准备（Docker、swap、部署目录、免密密钥）与仓库 Secrets 配置见 [docs/DEPLOY.md](docs/DEPLOY.md)
2. 推送到 `main` 触发 `.github/workflows/deploy.yml`：构建前端与后端 → rsync 产物到服务器 `/opt/ziyun-blog/releases/<git-sha>/` → 执行 `deploy/scripts/deploy.sh`
3. 发布脚本带健康检查，失败会自动回滚到上一个版本
4. HTTPS 由宿主机 certbot 签发 Let's Encrypt 证书并挂载进 Nginx 容器，续期自动生效

四个容器（nginx / backend / mysql / redis）已按 2C2G 服务器做内存预算，`deploy/docker-compose.yml` 中通过 `mem_limit` 限制上限。

## 相关文档

| 文档 | 内容 |
| --- | --- |
| [docs/DEPLOY.md](docs/DEPLOY.md) | 完整部署方案：服务器初始化、环境变量、Docker 与 Actions 配置、HTTPS 签发、回滚 |
| [docs/SEO.md](docs/SEO.md) | SEO 实施说明与站长平台（Google Search Console / Bing / 百度）提交流程 |

## 安全约定

- 仓库不包含任何真实密钥，所有凭据（数据库密码、JWT 密钥、对象存储 AK/SK、AI Key 加密密钥）一律经环境变量注入
- 对象存储建议使用 RAM 子账号，仅授予所属 bucket 的上传 / 删除权限
- AI 供应商 API Key 以 AES/GCM 加密后落库，数据库中不存明文
- 生产环境缺少 `JWT_SECRET` 等必填变量时应用会直接启动失败，防止弱配置上线

---

本项目为个人学习与实践项目，未附加开源许可证。
