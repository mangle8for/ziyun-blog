package fun.ziyun.blogserver.service;

import com.baomidou.mybatisplus.extension.service.IService;
import fun.ziyun.blogserver.common.PageResult;
import fun.ziyun.blogserver.dto.ArticleDTO;
import fun.ziyun.blogserver.entity.Article;
import fun.ziyun.blogserver.vo.ArticleDetailVO;
import fun.ziyun.blogserver.vo.ArticleListItemVO;

import java.util.List;

/**
 * 文章服务接口。
 *
 * <p>设计说明（IService 与 ServiceImpl 的便利性 —— 对照原生 MyBatis）：</p>
 * <pre>
 * 原生 MyBatis 时代：Controller 直接注入 Mapper，业务方法散落在 Controller
 * 里，事务注解 @Transactional 也只能加在 Controller 方法上（不推荐）。
 * MP 的 IService/ServiceImpl 提供：
 *   - 内置 CRUD（save/updateById/removeById/getById/list/page...），
 *     简单操作无需在接口里声明任何方法；
 *   - ServiceImpl 自带 mybatis-plus 的 SqlHelper，继承即获得
 *     BaseMapper 全部能力，且天然有 @Transactional 承载点。
 * 因此本接口只声明「MP 内置方法无法表达的业务查询」，其余继承。
 * </pre>
 */
public interface ArticleService extends IService<Article> {

    /**
     * 公开分页查询（仅已发布）。
     *
     * @param page       页码（>=1）
     * @param size       每页条数（1~50）
     * @param categoryId 按分类过滤，null 表示不过滤
     * @param tagId      按标签过滤，null 表示不过滤
     * @param keyword    标题模糊搜索，null/空表示不过滤
     */
    PageResult<ArticleListItemVO> pagePublished(long page, long size, Long categoryId, Long tagId, String keyword);

    /** 公开详情（仅已发布），带上一篇/下一篇导航 */
    ArticleDetailVO getPublishedDetail(Long id);

    /**
     * Sitemap 用：已发布文章的轻量清单（仅 id/updateTime，按更新时间倒序）。
     * 不走列表 VO 组装（免去分类/标签/作者关联查询），Sitemap 只需 URL 与 lastmod。
     */
    List<Article> listPublishedForSitemap();

    /** 管理端详情（含草稿，编辑器回填用；不拼上一篇/下一篇导航） */
    ArticleDetailVO getManageDetail(Long id);

    /**
     * 管理端分页查询（含草稿与已发布）。
     *
     * @param status 状态过滤，null 表示全部
     */
    PageResult<ArticleListItemVO> pageManage(long page, long size, Integer status);

    /** 新增文章（含标签关联维护），返回新文章 ID */
    Long create(ArticleDTO dto);

    /** 更新文章（含标签关联全量替换） */
    void update(Long id, ArticleDTO dto);

    /** 切换文章状态（草稿<->发布） */
    void changeStatus(Long id, Integer status);

    /** 删除文章（逻辑删 article + 物理删关联表记录） */
    void delete(Long id);
}
