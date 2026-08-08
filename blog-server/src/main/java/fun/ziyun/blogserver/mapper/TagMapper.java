package fun.ziyun.blogserver.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import fun.ziyun.blogserver.entity.Tag;
import fun.ziyun.blogserver.vo.TagHotVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 标签 Mapper。
 *
 * <p>设计说明（JOIN 查询的注解写法）：</p>
 * <pre>
 * 查询「某篇文章的所有标签」需要跨表 JOIN：
 *   tag 表 + article_tag 中间表 + 条件 at.article_id = ?
 * 直接手写 SQL 比用 MP 的 Wrapper 分两步查（先查关联再 in 查标签）
 * 更直观、一次查询完成。@Select 注解即写即用，无需 XML。
 * 注意别名 at / t 不可省 —— 两张表都有 id 列，不取别名会列名歧义。
 * </pre>
 */
public interface TagMapper extends BaseMapper<Tag> {

    /**
     * 查询某文章的全部标签（按标签名升序保证展示顺序稳定）。
     * 返回 List&lt;Tag&gt;：MyBatis 会根据方法返回类型自动映射行 -> 实体。
     */
    @Select("""
            SELECT t.id, t.name, t.create_time, t.update_time, t.deleted
            FROM tag t
            INNER JOIN article_tag at ON t.id = at.tag_id
            WHERE at.article_id = #{articleId}
            ORDER BY t.name ASC
            """)
    List<Tag> selectTagsByArticleId(@Param("articleId") Long articleId);

    /**
     * 热门标签：按「已发布文章数」倒序取前 limit 个。
     * 经 article_tag 中间表 JOIN article 过滤出已发布且未删除的文章；
     * 约定同 CategoryMapper.selectHotCategories（显式 deleted = 0、
     * 状态值由 Service 传入、INNER JOIN 剔除 0 文章标签）。
     */
    @Select("""
            SELECT t.id, t.name, COUNT(at.article_id) AS article_count
            FROM tag t
            INNER JOIN article_tag at ON at.tag_id = t.id
            INNER JOIN article a ON a.id = at.article_id AND a.status = #{status} AND a.deleted = 0
            WHERE t.deleted = 0
            GROUP BY t.id, t.name, t.create_time
            ORDER BY article_count DESC, t.create_time DESC
            LIMIT #{limit}
            """)
    List<TagHotVO> selectHotTags(@Param("status") int status, @Param("limit") int limit);
}
