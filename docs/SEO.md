# SEO 实施说明与站长平台提交流程

## 已实施的 SEO 能力

| 能力 | 实现 | 位置 |
| --- | --- | --- |
| 动态 Sitemap | 后端 `GET /api/v1/sitemap.xml`：首页/分类/标签/关于 + 全部已发布文章（含 lastmod） | `SitemapController.java` |
| robots.txt | 全站 Allow + Sitemap 声明 | `blog-web/public/robots.txt`（构建后由 nginx 直接服务） |
| 静态基础 meta | description / keywords / OG / Twitter Card | `blog-web/index.html` |
| 路由级动态 head | `@unhead/vue`：文章页 title=文章标题、description=摘要、canonical、og:image=封面；分类/标签/关于页各自标题 | 各视图 `useHead` |
| 结构化数据 | 文章页注入 JSON-LD `BlogPosting`（headline/datePublished/author/image） | `ArticleDetailView.vue` |
| 全局标题模板 | `%s · 紫云博客`（未设置标题的页面用默认文案） | `App.vue` |

架构说明：
- Sitemap 双路径可访问：后端 `GET /api/v1/sitemap.xml` + nginx 根路径别名 `/sitemap.xml`（推荐提交此 URL）；站点根地址可经 `SITE_URL` 环境变量覆盖（默认 `https://ziyun.fun`）。
- 当前是 SPA（JS 渲染），Google/Bing 能执行 JS 正常收录；百度对 SPA 收录较弱，如未来需要再评估动态渲染（见文末）。

## Google Search Console（推荐，主阵地）

1. 打开 <https://search.google.com/search-console>，用 Google 账号登录
2. 添加资源 → 选择「网址前缀」，输入 `https://ziyun.fun`
3. 验证方式选 **DNS 记录**（最省事，不需改动站点）：
   - 控制台给出 `TXT` 记录（如 `google-site-verification=xxx`）
   - 在域名服务商（ziyun.fun 的 DNS 面板）添加该 TXT 记录，等生效（几分钟~几小时）
4. 验证通过后，左侧「Sitemap」→ 提交 `sitemap.xml`（域名部分自动补齐；nginx 根路径已反代到后端）
5. 提交后 1~3 天可看到收录状态；用「网址检查」工具粘贴文章 URL 可手动请求收录（新文章发布后可手动提交加速）

## Bing Webmaster（可选，可一键导入 GSC）

1. 打开 <https://www.bing.com/webmasters>，用 Microsoft 账号登录
2. 创建站点 → 选择「从 Google Search Console 导入」→ 授权后自动带入验证与站点
3. Sitemap 提交 `sitemap.xml`
4. Bing 会定期从 GSC 同步数据，维护成本极低

## 百度站长平台（可选，SPA 收录较弱）

1. 打开 <https://ziyuan.baidu.com/>，站点管理 → 添加网站
2. 验证：HTML 标签验证需改 index.html；推荐「文件验证」——下载验证文件放 `blog-web/public/` 再构建部署
3. 提交 sitemap 需要「普通收录」权限（个人站点无主动推送资格，可做「手动提交」单条 URL）
4. 注意：百度对 JS 渲染（SPA）抓取能力差，若百度成为主要流量来源，建议启用动态渲染（见下）

## 收录情况自查

```bash
# 看 robots.txt / sitemap 是否可访问
curl -s https://ziyun.fun/robots.txt
curl -s https://ziyun.fun/sitemap.xml | head -20

# 检查文章页是否输出了完整 head（含 JSON-LD）
curl -s https://ziyun.fun/ | grep -o '<title>[^<]*</title>'
```

Google 搜索 `site:ziyun.fun` 查看已收录页面；GSC 的「覆盖范围」报告看收录异常。

## 后续增强（按需）

- **动态渲染（Dynamic Rendering）**：nginx 按 User-Agent（Baiduspider 等）把请求转发给 Puppeteer 渲染服务返回完整 HTML——解决百度收录；当前以 Google/Bing 为主可暂缓
- **OG 分享图**：目前文章页 og:image 用封面（无封面时无图）；可后续生成统一品牌分享图
- **页面预渲染**：构建期把静态路由渲染成 HTML（首页/关于），进一步降低首屏渲染对爬虫的依赖
