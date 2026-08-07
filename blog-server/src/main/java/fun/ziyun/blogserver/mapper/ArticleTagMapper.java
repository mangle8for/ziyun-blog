package fun.ziyun.blogserver.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import fun.ziyun.blogserver.entity.ArticleTag;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 文章-标签关联 Mapper。
 *
 * <p>设计说明（动态 SQL 的 &lt;script&gt; 写法）：</p>
 * <pre>
 * 批量插入数量不定（文章标签数 0~N 个），需要 <foreach> 动态拼 SQL，
 * 注解里用 <script> 包裹即可获得与 XML 完全一致的动态 SQL 能力。
 *
 * 为什么更新文章时用「先删后插」而不是「逐条比对差异」：
 *   1. 先删后插只需两条固定 SQL（DELETE 全量 + INSERT 全量），
 *      逻辑简单不易出错，事务保证原子性；
 *   2. 逐条 diff 要比较新旧集合，代码复杂度高、收益低 ——
 *      关联表没有业务意义需要保留的历史变更记录；
 *   3. 代价是索引页写放大，个人博客场景完全可以接受。
 * </pre>
 */
public interface ArticleTagMapper extends BaseMapper<ArticleTag> {

    /**
     * 批量插入关联。<foreach> 拼出：
     * INSERT INTO article_tag (article_id, tag_id) VALUES (?,?),(?,?)...
     */
    @Insert("""
            <script>
            INSERT INTO article_tag (article_id, tag_id) VALUES
            <foreach collection="tagIds" item="tagId" separator=",">
                (#{articleId}, #{tagId})
            </foreach>
            </script>
            """)
    int batchInsert(@Param("articleId") Long articleId, @Param("tagIds") List<Long> tagIds);

    /** 删除某文章的全部标签关联（更新文章时先清空再重插） */
    @Delete("DELETE FROM article_tag WHERE article_id = #{articleId}")
    int deleteByArticleId(@Param("articleId") Long articleId);

    /** 删除某标签的全部文章关联（删除标签时清理，避免悬空关联） */
    @Delete("DELETE FROM article_tag WHERE tag_id = #{tagId}")
    int deleteByTagId(@Param("tagId") Long tagId);

    /** 统计某标签被多少篇文章使用（删除标签前的引用检查） */
    @Select("SELECT COUNT(*) FROM article_tag WHERE tag_id = #{tagId}")
    int countByTagId(@Param("tagId") Long tagId);
}
