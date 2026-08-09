package fun.ziyun.blogserver.controller;

import fun.ziyun.blogserver.entity.Article;
import fun.ziyun.blogserver.service.ArticleService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Sitemap 生成接口（GET /api/v1/sitemap.xml，公开）。
 *
 * <p>设计说明：</p>
 * <pre>
 * 1. 为什么放 /api/v1/ 下：nginx 仅反代 /api/ 前缀，这样 sitemap 无需
 *    新增 nginx 路由；robots.txt 中的 Sitemap 声明指向该 URL 即可
 *    （搜索引擎不要求 sitemap 路径美观，可访问即可）。
 * 2. 动态生成而非静态文件：文章增删后永远最新，零运维；
 *    个人博客文章量小，每次全量拼装成本可忽略。
 * 3. 只包含「已发布」文章（草稿对爬虫无意义）；lastmod 用更新时间，
 *    帮助搜索引擎感知内容新鲜度。
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/sitemap.xml")
@RequiredArgsConstructor
public class SitemapController {

    private static final DateTimeFormatter LAST_MOD = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final ArticleService articleService;

    /** 站点根地址（与 nginx server_name 一致；生产可经 SITE_URL 环境变量覆盖） */
    @Value("${blog.site-url:https://ziyun.fun}")
    private String siteUrl;

    @GetMapping(produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<byte[]> sitemap() {
        StringBuilder xml = new StringBuilder(2048);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        // 静态页面：首页权重最高，分类/标签次之，关于页最低
        appendUrl(xml, siteUrl + "/", "1.0", null);
        appendUrl(xml, siteUrl + "/categories", "0.8", null);
        appendUrl(xml, siteUrl + "/tags", "0.8", null);
        appendUrl(xml, siteUrl + "/about", "0.6", null);

        // 文章详情：按更新时间倒序，全部已发布文章
        List<Article> articles = articleService.listPublishedForSitemap();
        for (Article article : articles) {
            String lastmod = article.getUpdateTime() == null
                    ? null
                    : article.getUpdateTime().format(LAST_MOD);
            appendUrl(xml, siteUrl + "/article/" + article.getId(), "0.9", lastmod);
        }

        xml.append("</urlset>\n");

        // 返回 byte[]：ByteArrayHttpMessageConverter 不会像 String 转换器那样
        // 追加 charset 参数 —— GSC 对 "application/xml;charset=UTF-8" 这类
        // 带参数 Content-Type 存在解析兼容性问题（曾报「无法读取站点地图」），
        // 显式返回严格无参数的 application/xml 最稳。
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .body(xml.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** 拼一条 &lt;url&gt; 记录（URL 均为站点固定前缀 + 数字 ID，无 XML 转义风险） */
    private void appendUrl(StringBuilder xml, String loc, String priority, String lastmod) {
        xml.append("  <url>\n");
        xml.append("    <loc>").append(loc).append("</loc>\n");
        if (lastmod != null) {
            xml.append("    <lastmod>").append(lastmod).append("</lastmod>\n");
        }
        xml.append("    <priority>").append(priority).append("</priority>\n");
        xml.append("  </url>\n");
    }
}
