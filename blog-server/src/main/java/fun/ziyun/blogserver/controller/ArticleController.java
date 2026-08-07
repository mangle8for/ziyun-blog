package fun.ziyun.blogserver.controller;

import fun.ziyun.blogserver.common.PageResult;
import fun.ziyun.blogserver.common.Result;
import fun.ziyun.blogserver.common.ResultCode;
import fun.ziyun.blogserver.dto.ArticleDTO;
import fun.ziyun.blogserver.dto.PageQuery;
import fun.ziyun.blogserver.dto.StatusUpdateDTO;
import fun.ziyun.blogserver.service.ArticleService;
import fun.ziyun.blogserver.vo.ArticleDetailVO;
import fun.ziyun.blogserver.vo.ArticleListItemVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文章接口（RESTful）。
 *
 * <p>设计说明（URL 语义与鉴权预留）：</p>
 * <pre>
 * 路由设计：
 *   GET  /api/v1/articles           公开：分页（仅已发布）
 *   GET  /api/v1/articles/manage    管理：分页（含草稿）—— 注意 /manage
 *                                    是「字面量路径」，必须声明在 /{id}
 *                                    之前，否则 "manage" 会被吞成 id 参数
 *   GET  /api/v1/articles/{id}      公开：详情
 *   POST /api/v1/articles           管理：新增
 *   PUT  /api/v1/articles/{id}      管理：更新
 *   PUT  /api/v1/articles/{id}/status 管理：状态切换
 *   DELETE /api/v1/articles/{id}   管理：删除
 *
 * P2 接入 Spring Security 后，写操作（POST/PUT/DELETE）由
 * SecurityConfig 统一 hasRole("ADMIN") 保护，本类无需改代码
 * （Controller 不感知鉴权，是过滤器链的职责）。
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/articles")
@RequiredArgsConstructor
@Validated
public class ArticleController {

    private final ArticleService articleService;

    /**
     * 公开分页列表。
     * 参数校验说明：PageQuery 上的 @Min/@Max 对 GET query 生效，
     * 依赖类级 @Validated（@RequestBody 场景用方法参数上的 @Valid，
     * 两种触发点不一样，这里都配齐，见 PageQuery 类注释）。
     */
    @GetMapping
    public Result<PageResult<ArticleListItemVO>> list(
            @Valid PageQuery query,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long tagId,
            @RequestParam(required = false) String keyword) {
        return Result.ok(articleService.pagePublished(
                query.getPage(), query.getSize(), categoryId, tagId, keyword));
    }

    /** 管理端列表（草稿/发布可过滤，status 为空查全部） */
    @GetMapping("/manage")
    public Result<PageResult<ArticleListItemVO>> manageList(
            @Valid PageQuery query,
            @RequestParam(required = false) Integer status) {
        return Result.ok(articleService.pageManage(query.getPage(), query.getSize(), status));
    }

    /**
     * 公开详情（含上一篇/下一篇）。
     * @Min 直接校验路径参数：非法的 id（<=0）无需进 Service，
     * 入口即拦截（400 而非 404，语义区分）。
     */
    @GetMapping("/{id}")
    public Result<ArticleDetailVO> detail(@PathVariable @Min(1) Long id) {
        return Result.ok(articleService.getPublishedDetail(id));
    }

    /** 新增文章，返回新文章 ID（前端拿到后可直接跳编辑页） */
    @PostMapping
    public Result<Long> create(@RequestBody @Valid ArticleDTO dto) {
        return Result.ok(articleService.create(dto));
    }

    /** 更新文章 */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable @Min(1) Long id, @RequestBody @Valid ArticleDTO dto) {
        articleService.update(id, dto);
        return Result.ok();
    }

    /** 状态切换：发布 <-> 草稿（P4 管理端「发布/取消发布」按钮直连本接口） */
    @PutMapping("/{id}/status")
    public Result<Void> changeStatus(@PathVariable @Min(1) Long id,
                                     @RequestBody @Valid StatusUpdateDTO dto) {
        articleService.changeStatus(id, dto.getStatus());
        return Result.ok();
    }

    /** 删除文章 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable @Min(1) Long id) {
        articleService.delete(id);
        return Result.ok();
    }
}
