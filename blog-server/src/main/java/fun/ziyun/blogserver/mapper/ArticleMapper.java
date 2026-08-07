package fun.ziyun.blogserver.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import fun.ziyun.blogserver.entity.Article;

/**
 * 文章 Mapper。
 *
 * <p>设计说明（BaseMapper 的便利性 —— 对照原生 MyBatis）：</p>
 * <pre>
 * 原生 MyBatis 要为一张表手写至少 6 个 SQL + resultMap：
 *   insert / deleteById / updateById / selectById / selectAll / count
 * 而这里 extends BaseMapper&lt;Article&gt; 后这 6 类基础方法全部内置，
 * 业务直接 articleMapper.selectById(id) 即可，无需任何 XML。
 * 需要复杂查询时（本文的分页条件拼接、JOIN），再追加自定义方法。
 * 本项目自定义 SQL 统一用注解方式写在接口里：
 *   优点：SQL 与 Java 方法同文件，改动直观；
 *   缺点：复杂 SQL 可读性差 —— 若未来 SQL 超过 5 行，建议迁移到
 *   resources/mapper/ArticleMapper.xml（application.yml 已配好
 *   mapper-locations 路径，两种方式可以共存）。
 * </pre>
 */
public interface ArticleMapper extends BaseMapper<Article> {
}
