package fun.ziyun.blogserver.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import fun.ziyun.blogserver.entity.Category;
import fun.ziyun.blogserver.vo.CategoryHotVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 分类 Mapper：基础 CRUD 由 BaseMapper 提供，自定义 SQL 仅热门统计一条。
 */
public interface CategoryMapper extends BaseMapper<Category> {

    /**
     * 热门分类：按「已发布文章数」倒序取前 limit 个。
     *
     * <p>注意（自定义 SQL 的两个显式约定）：</p>
     * <pre>
     * 1. @Select 注解 SQL 不经过 MyBatis-Plus 的逻辑删除拦截，
     *    category / article 两张表的 deleted = 0 都必须手写；
     * 2. 文章状态值由 Service 层传入（ArticleStatus.PUBLISHED.getCode()），
     *    避免 SQL 中散落魔法数字。
     * INNER JOIN 会自然剔除 0 文章的分类；
     * 计数并列时按创建时间倒序兜底，保证结果顺序稳定。
     * </pre>
     */
    @Select("""
            SELECT c.id, c.name, COUNT(a.id) AS article_count
            FROM category c
            INNER JOIN article a ON a.category_id = c.id AND a.status = #{status} AND a.deleted = 0
            WHERE c.deleted = 0
            GROUP BY c.id, c.name, c.create_time
            ORDER BY article_count DESC, c.create_time DESC
            LIMIT #{limit}
            """)
    List<CategoryHotVO> selectHotCategories(@Param("status") int status, @Param("limit") int limit);
}
