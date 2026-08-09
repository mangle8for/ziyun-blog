package fun.ziyun.blogserver.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import fun.ziyun.blogserver.common.PageResult;
import fun.ziyun.blogserver.common.ResultCode;
import fun.ziyun.blogserver.dto.ArticleDTO;
import fun.ziyun.blogserver.entity.Article;
import fun.ziyun.blogserver.entity.ArticleStatus;
import fun.ziyun.blogserver.entity.Category;
import fun.ziyun.blogserver.entity.Tag;
import fun.ziyun.blogserver.entity.User;
import fun.ziyun.blogserver.exception.BusinessException;
import fun.ziyun.blogserver.mapper.ArticleMapper;
import fun.ziyun.blogserver.mapper.ArticleTagMapper;
import fun.ziyun.blogserver.mapper.CategoryMapper;
import fun.ziyun.blogserver.mapper.TagMapper;
import fun.ziyun.blogserver.mapper.UserMapper;
import fun.ziyun.blogserver.service.ArticleService;
import fun.ziyun.blogserver.vo.ArticleDetailVO;
import fun.ziyun.blogserver.vo.ArticleListItemVO;
import fun.ziyun.blogserver.vo.ArticleNavVO;
import fun.ziyun.blogserver.vo.TagVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 文章服务实现（业务核心，注释详述关键设计）。
 */
@Service
@RequiredArgsConstructor
public class ArticleServiceImpl extends ServiceImpl<ArticleMapper, Article> implements ArticleService {

    /**
     * P1 阶段尚无登录体系，作者固定取预置管理员 ID。
     * P2 接入 Security 后改为从 SecurityContext 读取当前登录用户。
     */
    private static final Long DEFAULT_AUTHOR_ID = 1L;

    private final ArticleTagMapper articleTagMapper;
    private final TagMapper tagMapper;
    private final CategoryMapper categoryMapper;
    private final UserMapper userMapper;

    // ==================== 公开查询 ====================

    @Override
    public PageResult<ArticleListItemVO> pagePublished(long page, long size, Long categoryId, Long tagId, String keyword) {
        // LambdaQueryWrapper：方法引用 Article::getStatus 替代硬编码列名，
        // 列改名时编译器直接报错，不会出现「改库漏改 SQL」的运行时问题。
        // 对照原生 MyBatis：动态条件要靠 XML <if test> 逐段拼，这里
        // 每个条件方法第一个 boolean 参数为 false 时整段跳过 ——
        // 动态拼接收敛成一行一个条件，可读性大幅提升。
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<Article>()
                .eq(Article::getStatus, ArticleStatus.PUBLISHED.getCode())
                // categoryId != null 才拼 AND category_id = ?
                .eq(categoryId != null, Article::getCategoryId, categoryId)
                // 标题模糊搜索（keyword 无内容时不拼）
                .like(StringUtils.hasText(keyword), Article::getTitle, keyword)
                // 标签过滤：子查询查出「挂了该标签的文章 ID 集合」。
                // 安全说明：tagId 是 Long 数值类型，此拼接无注入风险；
                // 若未来改为字符串条件，必须换成参数化写法。
                .inSql(tagId != null, Article::getId,
                        "SELECT article_id FROM article_tag WHERE tag_id = " + tagId)
                // 列表按发布时间倒序，最新在前
                .orderByDesc(Article::getCreateTime);

        Page<Article> pageResult = this.page(new Page<>(page, size), wrapper);
        // convert()：把每行实体转成 VO（Page 泛型转换的 MP 内置能力），
        // 再统一交给 PageResult.from 包装 —— 与 Controller 层零耦合。
        return PageResult.from(pageResult.convert(this::toListItemVO));
    }

    @Override
    public ArticleDetailVO getPublishedDetail(Long id) {
        Article article = getPublishedById(id);
        return toDetailVO(article);
    }

    @Override
    public List<Article> listPublishedForSitemap() {
        return this.lambdaQuery()
                // 只查 Sitemap 需要的列（避免把 LONGTEXT 正文整块拉回来）
                .select(Article::getId, Article::getUpdateTime)
                .eq(Article::getStatus, ArticleStatus.PUBLISHED.getCode())
                .orderByDesc(Article::getUpdateTime)
                .list();
    }

    @Override
    public ArticleDetailVO getManageDetail(Long id) {
        // 管理端编辑器回填需要草稿内容，不限制发布状态；
        // 上一篇/下一篇是前台导航语义，管理端不需要（且基于发布状态排序）
        Article article = getEntityById(id);
        ArticleDetailVO vo = new ArticleDetailVO();
        BeanUtils.copyProperties(article, vo);
        vo.setCategoryName(getCategoryName(article.getCategoryId()));
        vo.setAuthorName(getAuthorName(article.getAuthorId()));
        vo.setTags(getTagsOfArticle(article.getId()));
        return vo;
    }

    // ==================== 管理端 ====================

    @Override
    public PageResult<ArticleListItemVO> pageManage(long page, long size, Integer status) {
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<Article>()
                .eq(status != null, Article::getStatus, status)
                .orderByDesc(Article::getUpdateTime);
        Page<Article> pageResult = this.page(new Page<>(page, size), wrapper);
        return PageResult.from(pageResult.convert(this::toListItemVO));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(ArticleDTO dto) {
        checkCategoryExists(dto.getCategoryId());

        Article article = new Article();
        // DTO -> 实体：只拷贝用户可控的业务字段；
        // authorId/viewCount/likeCount 由服务端决定，拒绝客户端伪造
        article.setTitle(dto.getTitle());
        article.setSummary(dto.getSummary());
        article.setContent(dto.getContent());
        article.setCover(dto.getCover());
        article.setCategoryId(dto.getCategoryId());
        article.setAuthorId(DEFAULT_AUTHOR_ID);
        // status 允许客户端直接指定（「保存草稿」与「立即发布」是两种常见操作）
        article.setStatus(dto.getStatus());
        article.setViewCount(0);
        article.setLikeCount(0);
        // save() 触发雪花 ID 生成 + 时间字段填充（见 MetaObjectHandler）
        this.save(article);

        // 标签关联与文章本体同事务（方法上的 @Transactional 保证原子性）
        replaceTags(article.getId(), dto.getTagIds());
        return article.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, ArticleDTO dto) {
        Article article = getEntityById(id);
        checkCategoryExists(dto.getCategoryId());

        article.setTitle(dto.getTitle());
        article.setSummary(dto.getSummary());
        article.setContent(dto.getContent());
        article.setCover(dto.getCover());
        article.setCategoryId(dto.getCategoryId());
        article.setStatus(dto.getStatus());
        // updateById 配合 strictUpdateFill 时，若实体 updateTime 非 null 不会覆盖，
        // 因此业务层显式设置当前时间，确保修改后 update_time 必然刷新。
        article.setUpdateTime(LocalDateTime.now());
        this.updateById(article);

        replaceTags(id, dto.getTagIds());
    }

    @Override
    public void changeStatus(Long id, Integer status) {
        Article article = getEntityById(id);
        // 幂等短路：重复提交同状态直接返回，不产生无意义 UPDATE
        if (article.getStatus().equals(status)) {
            return;
        }
        article.setStatus(status);
        article.setUpdateTime(LocalDateTime.now());
        this.updateById(article);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        getEntityById(id);
        // 中间表无 deleted 字段，物理删除关联（两表都清，事务保证一致）
        articleTagMapper.deleteByArticleId(id);
        // 逻辑删除：MP 自动改写为 UPDATE article SET deleted=1 WHERE id=? AND deleted=0
        this.removeById(id);
    }

    // ==================== 私有工具 ====================

    /** 校验分类存在（无物理外键，引用完整性靠业务代码保证） */
    private void checkCategoryExists(Long categoryId) {
        if (categoryId == null) {
            return;
        }
        if (categoryMapper.selectById(categoryId) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "分类不存在，id=" + categoryId);
        }
    }

    /**
     * 按 id 查实体。所有管理接口的公共入口，统一「非法 ID -> 404」语义。
     * getById 自带逻辑删除过滤：已删文章查询结果为 null。
     */
    private Article getEntityById(Long id) {
        Article article = this.getById(id);
        if (article == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "文章不存在，id=" + id);
        }
        return article;
    }

    /** 按 id 查「已发布」文章（公开详情使用，草稿对前台返回 404 而非暴露） */
    private Article getPublishedById(Long id) {
        Article article = this.lambdaQuery()
                .eq(Article::getId, id)
                .eq(Article::getStatus, ArticleStatus.PUBLISHED.getCode())
                .one();
        if (article == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "文章不存在或未发布，id=" + id);
        }
        return article;
    }

    /**
     * 全量替换文章标签关联（先删后插，见 ArticleTagMapper 设计说明）。
     * 事务边界由调用方（create/update）的 @Transactional 保证。
     */
    private void replaceTags(Long articleId, List<Long> tagIds) {
        articleTagMapper.deleteByArticleId(articleId);
        // 去重：客户端可能重复提交同一标签，直接插入会被复合主键拒绝
        // （DuplicateKeyException 会落到 500 兜底，体验不好），先过滤更友好
        List<Long> distinctTagIds = tagIds == null
                ? Collections.emptyList()
                : tagIds.stream().distinct().toList();
        if (!distinctTagIds.isEmpty()) {
            articleTagMapper.batchInsert(articleId, distinctTagIds);
        }
    }

    /**
     * 查找相邻文章（公开可见范围内）。
     *
     * <p>设计说明（时间戳排序 + 同秒并列）：</p>
     * <pre>
     * 上一篇/下一篇按 create_time 排序，而非雪花 ID —— 雪花 ID 虽按时间
     * 递增，但只在同毫秒内保证顺序，跨机器/跨秒可能与 create_time 背离。
     * 极端情况：两篇文章同一秒发布，仅比较 create_time 会漏掉一条，
     * 因此同秒时退化用 id 比较（id 大者视为更新），保证边界不丢。
     * 组合条件用 and(w -> ...) 分组，避免 MP 的 or() 把整条 WHERE 拼乱。
     * </pre>
     */
    private ArticleNavVO findNavArticle(Article current, boolean isPrev) {
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<Article>()
                .eq(Article::getStatus, ArticleStatus.PUBLISHED.getCode())
                .ne(Article::getId, current.getId());
        if (isPrev) {
            wrapper.and(w -> w
                            .lt(Article::getCreateTime, current.getCreateTime())
                            .or(x -> x.eq(Article::getCreateTime, current.getCreateTime())
                                    .lt(Article::getId, current.getId())))
                    .orderByDesc(Article::getCreateTime)
                    .orderByDesc(Article::getId);
        } else {
            wrapper.and(w -> w
                            .gt(Article::getCreateTime, current.getCreateTime())
                            .or(x -> x.eq(Article::getCreateTime, current.getCreateTime())
                                    .gt(Article::getId, current.getId())))
                    .orderByAsc(Article::getCreateTime)
                    .orderByAsc(Article::getId);
        }
        // last("LIMIT 1")：Wrapper 不支持 limit 方法，last 追加原始片段
        // （值全部来自框架参数化，无注入面）
        Article nav = this.getOne(wrapper.last("LIMIT 1"));
        if (nav == null) {
            return null; // 已是第一篇/最后一篇
        }
        ArticleNavVO vo = new ArticleNavVO();
        vo.setId(nav.getId());
        vo.setTitle(nav.getTitle());
        return vo;
    }

    // ==================== 实体 -> VO 转换 ====================

    /**
     * 实体 -> 列表 VO。
     * 注意：每篇文章要补查分类名/作者名/标签（3 次小查询），
     * 一页 10 篇就是 30 次 —— 经典 N+1 问题。个人博客量级下可接受，
     * 且 SQL 都命中主键索引。若数据量上来，优化方向是把本页涉及
     * 的 id 收集后用 selectBatchIds 一次取回，内存里组 Map 拼装
     * （3 次查询替代 30 次）。此处保留直观写法便于学习，
     * 不提前引入优化复杂度。
     */
    private ArticleListItemVO toListItemVO(Article article) {
        ArticleListItemVO vo = new ArticleListItemVO();
        // 同名属性（id/title/summary/cover/计数/状态/时间）批量拷贝；
        // 目标 VO 独有的 categoryName/authorName/tags 源里没有，不受影响
        BeanUtils.copyProperties(article, vo);
        vo.setCategoryName(getCategoryName(article.getCategoryId()));
        vo.setAuthorName(getAuthorName(article.getAuthorId()));
        vo.setTags(getTagsOfArticle(article.getId()));
        return vo;
    }

    /** 实体 -> 详情 VO（比列表多 content 与上一篇/下一篇） */
    private ArticleDetailVO toDetailVO(Article article) {
        ArticleDetailVO vo = new ArticleDetailVO();
        BeanUtils.copyProperties(article, vo);
        vo.setCategoryName(getCategoryName(article.getCategoryId()));
        vo.setAuthorName(getAuthorName(article.getAuthorId()));
        vo.setTags(getTagsOfArticle(article.getId()));
        vo.setPrevArticle(findNavArticle(article, true));
        vo.setNextArticle(findNavArticle(article, false));
        return vo;
    }

    private String getCategoryName(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        Category category = categoryMapper.selectById(categoryId);
        return category == null ? null : category.getName();
    }

    private String getAuthorName(Long authorId) {
        User user = userMapper.selectById(authorId);
        // 昵称为空时回退用户名，保证展示不为空
        return user == null ? null
                : (user.getNickname() != null ? user.getNickname() : user.getUsername());
    }

    private List<TagVO> getTagsOfArticle(Long articleId) {
        List<Tag> tags = tagMapper.selectTagsByArticleId(articleId);
        return tags.stream().map(tag -> {
            TagVO tagVO = new TagVO();
            tagVO.setId(tag.getId());
            tagVO.setName(tag.getName());
            return tagVO;
        }).toList();
    }
}
